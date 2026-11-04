package org.paron.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/profile")
public class UserProfileController {
    private final UserProfileRepository profiles;

    public UserProfileController(UserProfileRepository profiles) {
        this.profiles = profiles;
    }

    @PostMapping
    public ResponseEntity<ProfileResponse> saveProfile(
            @RequestHeader("X-User-Id") String authenticatedUserId,
            @Valid @RequestBody ProfileRequest request) {
        UserProfile profile = profiles.findById(authenticatedUserId)
                .orElseGet(() -> new UserProfile(authenticatedUserId, request.name().trim(), request.email().trim()));
        profile.setName(request.name().trim());
        profile.setEmail(request.email().trim());
        UserProfile saved = profiles.save(profile);
        return ResponseEntity.ok(new ProfileResponse(saved.getUserId(), saved.getName(), saved.getEmail()));
    }

    public record ProfileRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email) {}

    public record ProfileResponse(String userId, String name, String email) {}
}
