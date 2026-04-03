package ru.mfa.airline.dto;

import ru.mfa.airline.model.User;

public class UserResponse {
    private Long id;
    private String username;
    private String role;

    public UserResponse() {
    }

    public UserResponse(Long id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}