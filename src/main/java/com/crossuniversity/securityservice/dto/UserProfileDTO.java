package com.crossuniversity.securityservice.dto;

import com.crossuniversity.securityservice.entity.UniversityUser;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@Schema(description = "An object representing a full user profile")

public class UserProfileDTO {
    private Long id;
    private String userName;
    private String email;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double space;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UniversityDTO university;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<LibraryDTO> ownLibraries;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<LibraryDTO> subscribedLibraries;

    public static UserProfileDTO parseEntityToDto(UniversityUser universityUser){
        UniversityDTO universityDTO = UniversityDTO.parseEntityToDto(universityUser.getUniversity());
        List<LibraryDTO> ownLibrariesDTO = universityUser.getOwnLibraries()
                .stream().map(LibraryDTO::parseEntityToDto).toList();

        List<LibraryDTO> subscribedLibrariesDTO = universityUser.getSubscribedLibraries()
                .stream().map(LibraryDTO::parseEntityToDto).toList();

        return UserProfileDTO.builder()
                .id(universityUser.getId())
                .userName(universityUser.getUserName())
                .university(universityDTO)
                .email(universityUser.getUserCredentials().getEmail())
                .space(universityUser.getSpace())
                .ownLibraries(ownLibrariesDTO)
                .subscribedLibraries(subscribedLibrariesDTO)
                .build();
    }
}
