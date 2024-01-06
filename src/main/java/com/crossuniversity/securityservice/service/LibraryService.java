package com.crossuniversity.securityservice.service;

import com.crossuniversity.securityservice.dto.DocumentDTO;
import com.crossuniversity.securityservice.dto.LibraryDTO;
import com.crossuniversity.securityservice.dto.UserBriefProfile;
import com.crossuniversity.securityservice.entity.Document;
import com.crossuniversity.securityservice.entity.Library;
import com.crossuniversity.securityservice.entity.UniversityUser;
import com.crossuniversity.securityservice.exception.DocumentNotFoundException;
import com.crossuniversity.securityservice.exception.LibraryNotFoundException;
import com.crossuniversity.securityservice.exception.UniversityNotFoundException;
import com.crossuniversity.securityservice.exception.UserNotFoundException;
import com.crossuniversity.securityservice.mapper.BriefProfileMapper;
import com.crossuniversity.securityservice.mapper.DocumentMapper;
import com.crossuniversity.securityservice.mapper.LibraryMapper;
import com.crossuniversity.securityservice.repository.DocumentRepository;
import com.crossuniversity.securityservice.repository.LibraryRepository;
import com.crossuniversity.securityservice.repository.UniversityRepository;
import com.crossuniversity.securityservice.repository.UniversityUserRepository;
import com.crossuniversity.securityservice.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.expression.AccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class LibraryService {
    private final UniversityUserRepository universityUserRepository;
    private final UniversityRepository universityRepository;
    private final LibraryRepository libraryRepository;
    private final LibraryMapper libraryMapper;
    private final DocumentMapper documentMapper;
    private final BriefProfileMapper briefProfileMapper;
    private final DocumentRepository documentRepository;
    private final SecurityUtils securityUtils;

    @Autowired
    public LibraryService(UniversityUserRepository universityUserRepository,
                          UniversityRepository universityRepository,
                          LibraryRepository libraryRepository,
                          LibraryMapper libraryMapper,
                          DocumentMapper documentMapper,
                          BriefProfileMapper briefProfileMapper,
                          DocumentRepository documentRepository,
                          SecurityUtils securityUtils) {
        this.universityUserRepository = universityUserRepository;
        this.universityRepository = universityRepository;
        this.libraryRepository = libraryRepository;
        this.libraryMapper = libraryMapper;
        this.documentMapper = documentMapper;
        this.briefProfileMapper = briefProfileMapper;
        this.documentRepository = documentRepository;
        this.securityUtils = securityUtils;
    }

    public List<LibraryDTO> getOwnLibraries() {
        UniversityUser universityUser = securityUtils.getUserFromSecurityContextHolder();
        return libraryMapper.mapToListDTO(universityUser.getOwnLibraries());
    }

    public List<LibraryDTO> getSubscribedLibraries() {
        UniversityUser universityUser = securityUtils.getUserFromSecurityContextHolder();
        return libraryMapper.mapToListDTO(universityUser.getSubscribedLibraries());
    }

    public List<LibraryDTO> getUniversityLibraries(Long universityId) {
        if (!universityRepository.existsById(universityId))
            throw new UniversityNotFoundException("University with id = " + universityId + " does not exist");
        return libraryMapper.mapToListDTO(libraryRepository
                .findLibrariesByUniversityIdAndLibraryAccess(universityId, true));
    }

    public List<UserBriefProfile> getOwnersList(Long libraryId) {
        Library library = getLibraryById(libraryId);
        return briefProfileMapper.mapToListDTO(library.getOwners());
    }

    public List<UserBriefProfile> getSubscribersList(Long libraryId) {
        Library library = getLibraryById(libraryId);
        return briefProfileMapper.mapToListDTO(library.getSubscribers());
    }

    public List<DocumentDTO> getDocumentsByLibraryId(Long libraryId) throws AccessException {
        Library library = getLibraryById(libraryId);
        UniversityUser universityUser = securityUtils.getUserFromSecurityContextHolder();

        if (!library.isLibraryAccess()) {
            if (checkLibraryOwnerAccess(libraryId, universityUser) ||
                    checkLibrarySubscriberAccess(libraryId, universityUser)) {
                return documentMapper.mapListToDTO(library.getDocuments());
            }
        } else return documentMapper.mapListToDTO(library.getDocuments());
        throw new AccessException("Access forbidden");
    }


    public LibraryDTO createLibrary(LibraryDTO libraryDTO) {
        UniversityUser universityUser = securityUtils.getUserFromSecurityContextHolder();
        Library library = libraryMapper.mapToEntity(libraryDTO);

        library.setUniversity(universityUser.getUniversity());
        libraryRepository.save(library);

        universityUser.addOwnLibrary(library);
        universityUserRepository.save(universityUser);
        return libraryMapper.mapToDTO(library);
    }

    public List<LibraryDTO> subscribeToLibrary(Long libraryId) {
        UniversityUser universityUser = securityUtils.getUserFromSecurityContextHolder();
        Library library = getLibraryById(libraryId);

        universityUser.addSubscribedLibrary(library);
        universityUserRepository.save(universityUser);
        return libraryMapper.mapToListDTO(universityUser.getSubscribedLibraries());
    }

    public void subscribeUser(Long libraryId, String email) throws AccessException {
        UniversityUser owner = securityUtils.getUserFromSecurityContextHolder();

        if (checkLibraryOwnerAccess(libraryId, owner)) {
            Library library = getLibraryById(libraryId);
            UniversityUser universityUser = universityUserRepository.findUniversityUserByEmail(email)
                    .orElseThrow(() -> new UserNotFoundException("User with email = " + email + " not found"));

            universityUser.addSubscribedLibrary(library);
            universityUserRepository.save(universityUser);
        } else throw new AccessException("Access restricted");
    }

    public void unsubscribeUser(Long libraryId, String email) throws AccessException {
        UniversityUser owner = securityUtils.getUserFromSecurityContextHolder();

        if (checkLibraryOwnerAccess(libraryId, owner)) {
            Library library = getLibraryById(libraryId);
            UniversityUser universityUser = universityUserRepository.findUniversityUserByEmail(email)
                    .orElseThrow(() -> new UserNotFoundException("User with email = " + email + " not found"));

            universityUser.removeSubscribedLibrary(library);
            universityUserRepository.save(universityUser);
        } else throw new AccessException("Access restricted");
    }

    public List<LibraryDTO> unsubscribeLibrary(Long libraryId) {
        UniversityUser universityUser = securityUtils.getUserFromSecurityContextHolder();
        Library library = getLibraryById(libraryId);

        universityUser.removeSubscribedLibrary(library);
        universityUserRepository.save(universityUser);
        return libraryMapper.mapToListDTO(universityUser.getSubscribedLibraries());
    }

    public Resource downloadDocument(Long documentId) throws MalformedURLException {
        Document document = getDocumentById(documentId);
        Path path = Paths.get(document.getFilePath());
        return new UrlResource(path.toUri());
    }

    public DocumentDTO uploadDocument(MultipartFile file,
                                      String title, String topic, String description,
                                      Long libraryId) throws IOException, AccessException {
        UniversityUser universityUser = securityUtils.getUserFromSecurityContextHolder();

        if (checkLibraryOwnerAccess(libraryId, universityUser)) {
            Library library = libraryRepository.findById(libraryId).orElseThrow();

            String path = "D:\\CrossUniversityLibrary\\";

            Document document = Document.builder()
                    .title(title)
                    .topic(topic)
                    .description(description)
                    .filePath(path + file.getOriginalFilename())
                    .fileSize(file.getSize() / 1_000_000.0)
                    .libraries(new ArrayList<>())
                    .owner(universityUser)
                    .build();

            universityUser.decreaseSpace(document.getFileSize());
            library.addDocument(document);

            documentRepository.save(document);

            Path filePath = Paths.get(path, file.getOriginalFilename());
            file.transferTo(filePath.toFile());

            return documentMapper.mapToDTO(document);
        } else throw new AccessException("Access restricted");
    }

    public LibraryDTO updateLibrary(LibraryDTO libraryDTO) throws AccessException {
        UniversityUser owner = securityUtils.getUserFromSecurityContextHolder();

        Long libraryId = libraryDTO.getId();

        if (checkLibraryOwnerAccess(libraryId, owner)) {
            Library library = getLibraryById(libraryId);

            Library updatedLibrary = libraryMapper.updateEntity(libraryDTO, library);

            libraryRepository.save(updatedLibrary);

            return libraryMapper.mapToDTO(updatedLibrary);
        } else throw new AccessException("Access restricted");
    }

    public void deleteLibrary(Long libraryId) throws AccessException {
        UniversityUser owner = securityUtils.getUserFromSecurityContextHolder();

        if (checkLibraryOwnerAccess(libraryId, owner)) {
            libraryRepository.deleteLibrary(libraryId);
            log.info("Delete from library table");
        } else throw new AccessException("Access restricted");
    }

    public List<LibraryDTO> findLibrariesBy(Long universityId, String title, String topic, String ownerEmail) {
        if (title == null && topic == null && ownerEmail == null)
            return getUniversityLibraries(universityId);

        if (topic == null && ownerEmail == null)
            return libraryMapper.mapToListDTO(libraryRepository.findLibrariesByTitle(title));

        if (title == null && ownerEmail == null)
            return libraryMapper.mapToListDTO(libraryRepository.findLibrariesByTopic(topic));

        if (title == null && topic == null)
            return libraryMapper.mapToListDTO(libraryRepository.findLibrariesByOwner(ownerEmail));

        if (title == null)
            return libraryMapper.mapToListDTO(libraryRepository.findLibrariesByTopicAndOwner(topic, ownerEmail));

        if (topic == null)
            return libraryMapper.mapToListDTO(libraryRepository.findLibrariesByTitleAndOwner(title, ownerEmail));

        if (ownerEmail == null)
            return libraryMapper.mapToListDTO(libraryRepository.findLibrariesByTitleAndTopic(title, topic));

        return libraryMapper.mapToListDTO(libraryRepository.findLibrariesByTitleAndTopicAndOwner(title, topic, ownerEmail));
    }

    public void addExistedDocumentToLibrary(Long libraryId, Long documentId) throws AccessException {
        UniversityUser universityUser = securityUtils.getUserFromSecurityContextHolder();
        Library library = getLibraryById(libraryId);
        if (checkLibraryOwnerAccess(libraryId, universityUser)) {
            Document document = getDocumentById(documentId);
            library.addDocument(document);

            libraryRepository.save(library);
        } else throw new AccessException("Access forbidden");
    }

    public void deleteDocument(Long documentId) throws AccessException {
        UniversityUser universityUser = securityUtils.getUserFromSecurityContextHolder();
        if (checkDocumentOwnerAccess(documentId, universityUser)) {
            Document document = getDocumentById(documentId);

            universityUser.increaseSpace(document.getFileSize());

            documentRepository.deleteDocumentById(documentId);
        } else throw new AccessException("Access forbidden");
    }

    public void removeExistedDocumentFromLibrary(Long libraryId, Long documentId) throws AccessException {
        UniversityUser universityUser = securityUtils.getUserFromSecurityContextHolder();
        Library library = getLibraryById(libraryId);
        if (checkLibraryOwnerAccess(libraryId, universityUser)) {
            Document document = getDocumentById(documentId);
            library.removeDocument(document);

            libraryRepository.save(library);

            List<Library> libraries = document.getLibraries();
            if (libraries.isEmpty())
                documentRepository.deleteDocumentById(documentId);
        } else throw new AccessException("Access forbidden");
    }

    public DocumentDTO updateDocument(DocumentDTO documentDTO) throws AccessException {
        UniversityUser universityUser = securityUtils.getUserFromSecurityContextHolder();
        if (checkDocumentOwnerAccess(documentDTO.getId(), universityUser)) {
            Document document = getDocumentById(documentDTO.getId());

            document.setTitle(documentDTO.getTitle());
            document.setTopic(documentDTO.getTopic());
            document.setDescription(documentDTO.getDescription());

            documentRepository.save(document);
            return documentMapper.mapToDTO(document);
        } else throw new AccessException("Access forbidden");
    }

    private Library getLibraryById(Long libraryId) {
        return libraryRepository.findById(libraryId)
                .orElseThrow(() -> new LibraryNotFoundException("Library with id = " + libraryId + " does not exist"));
    }

    private Document getDocumentById(Long documentId) {
        return documentRepository.findById(documentId).orElseThrow(() ->
                new DocumentNotFoundException("Document with id = " + documentId + " does not exist"));
    }

    private boolean checkLibraryOwnerAccess(Long libraryId, UniversityUser universityUser) {
        boolean flag = false;
        for (Library library : universityUser.getOwnLibraries()) {
            if (library.getId().equals(libraryId))
                return true;
        }
        return flag;
    }

    private boolean checkLibrarySubscriberAccess(Long libraryId, UniversityUser universityUser) {
        for (Library library : universityUser.getSubscribedLibraries()) {
            if (library.getId().equals(libraryId))
                return true;
        }
        return false;
    }

    private boolean checkDocumentOwnerAccess(Long documentId, UniversityUser universityUser) {
        for (Document document : universityUser.getDocuments()) {
            if (document.getId().equals(documentId))
                return true;
        }
        return false;
    }
}
