package org.paron.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_user_profiles")
public class UserProfile {
    @Id
    @Column(name = "user_id", nullable = false, length = 32)
    private String userId;

    @Column(name = "display_name", nullable = false, length = 120)
    private String name;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserProfile() {}

    public UserProfile(String userId, String name, String email) {
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

    @PrePersist
    void beforeInsert() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
}
