package com.associago.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import com.associago.suite.SuiteException;
import org.springframework.core.env.Environment;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final Environment environment;

    @Autowired
    public UserController(UserService userService, Environment environment) {
        this.userService = userService;
        this.environment = environment;
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        if (environment.matchesProfiles("cloud") && (user.getId() != null || user.getClerkUserId() != null)) {
            throw new SuiteException(400, "USER_IDENTITY_FORBIDDEN", "Una nuova anagrafica non può sostituire un'identità o un collegamento esistente.");
        }
        return userService.save(user);
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User userDetails) {
        return userService.findById(id)
                .map(existingUser -> {
                    if (environment.matchesProfiles("cloud") && (userDetails.getId() != null && !Objects.equals(id, userDetails.getId())
                            || userDetails.getClerkUserId() != null && !Objects.equals(existingUser.getClerkUserId(), userDetails.getClerkUserId()))) {
                        throw new SuiteException(409, "USER_IDENTITY_IMMUTABLE", "L'identità e il collegamento di accesso originali non possono essere riassegnati.");
                    }
                    existingUser.setFirstName(userDetails.getFirstName());
                    existingUser.setLastName(userDetails.getLastName());
                    existingUser.setTaxCode(userDetails.getTaxCode());
                    existingUser.setEmail(userDetails.getEmail());
                    existingUser.setPhone(userDetails.getPhone());
                    existingUser.setBirthDate(userDetails.getBirthDate());
                    existingUser.setBirthPlace(userDetails.getBirthPlace());
                    existingUser.setAddress(userDetails.getAddress());
                    existingUser.setCity(userDetails.getCity());
                    existingUser.setZipCode(userDetails.getZipCode());
                    return ResponseEntity.ok(userService.save(existingUser));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (userService.findById(id).isPresent()) {
            userService.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
