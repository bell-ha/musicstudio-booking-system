package com.musicstudio.organization.api;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.musicstudio.common.security.AccessTokens;
import com.musicstudio.organization.application.OrganizationService;
import com.musicstudio.organization.domain.MembershipRole;
import com.musicstudio.organization.domain.MembershipStatus;
import com.musicstudio.organization.domain.Module;
import com.musicstudio.organization.domain.Organization;
import com.musicstudio.organization.domain.OrganizationType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
class OrganizationController {

    private final OrganizationService organizationService;

    OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    OrganizationResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest request) {
        Organization o = organizationService.create(AccessTokens.userId(jwt),
                request.name(), request.type(), request.timezone(), request.modules());
        return new OrganizationResponse(o.getId(), o.getName(), o.getType(), o.getTimezone(), o.getModules(),
                o.getJoinCode());
    }

    @GetMapping("/me/organizations")
    List<MyOrganizationResponse> myOrganizations(@AuthenticationPrincipal Jwt jwt) {
        return organizationService.myOrganizations(AccessTokens.userId(jwt)).stream()
                .map(r -> new MyOrganizationResponse(r.getOrganization().getId(), r.getOrganization().getName(),
                        r.getOrganization().getType(), r.getMembership().getRole(), r.getMembership().getStatus(),
                        r.getOrganization().getModules(), r.getOrganization().getTimezone()))
                .toList();
    }

    record CreateRequest(@NotBlank @Size(max = 100) String name,
                         @NotNull OrganizationType type,
                         @NotBlank String timezone,
                         Set<Module> modules) {
    }

    record OrganizationResponse(Long id, String name, OrganizationType type, String timezone, Set<Module> modules,
                                String joinCode) {
    }

    record MyOrganizationResponse(Long organizationId, String name, OrganizationType type, MembershipRole role,
                                  MembershipStatus status, Set<Module> modules, String timezone) {
    }
}
