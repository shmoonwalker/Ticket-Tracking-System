package net.hackyourfuture.tickettrackingsystem.service;

import lombok.RequiredArgsConstructor;
import net.hackyourfuture.tickettrackingsystem.dto.request.CreateUserRequest;
import net.hackyourfuture.tickettrackingsystem.dto.request.UpdateUserRequest;
import net.hackyourfuture.tickettrackingsystem.dto.response.UserResponse;
import net.hackyourfuture.tickettrackingsystem.exception.DuplicateEmailException;
import net.hackyourfuture.tickettrackingsystem.exception.ResourceNotFoundException;
import net.hackyourfuture.tickettrackingsystem.model.User;
import net.hackyourfuture.tickettrackingsystem.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException(
                    "Email " + request.email() + " already exists");
        }

        User createdUser = userRepository.create(new User(
                null,
                request.name(),
                request.email()
        ));

        return toUserResponse(createdUser);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toUserResponse)
                .toList();
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User with id " + id + " not found"));

        return toUserResponse(user);
    }

    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User with id " + id + " not found"));

        Optional<User> userWithSameEmail =
                userRepository.findByEmail(request.email());

        if (userWithSameEmail.isPresent()
                && !userWithSameEmail.get().id().equals(id)) {
            throw new DuplicateEmailException(
                    "Email " + request.email() + " already exists");
        }

        User updatedUser = new User(
                existingUser.id(),
                request.name(),
                request.email()
        );

        User savedUser = userRepository.update(updatedUser);

        return toUserResponse(savedUser);
    }

    public void deleteUser(Long id) {
        boolean deleted = userRepository.deleteById(id);

        if (!deleted) {
            throw new ResourceNotFoundException(
                    "User with id " + id + " not found");
        }
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.id(),
                user.name(),
                user.email()
        );
    }
}