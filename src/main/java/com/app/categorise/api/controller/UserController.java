    package com.app.categorise.api.controller;

    import com.app.categorise.domain.model.User;
    import com.app.categorise.domain.service.UserService;
    import com.app.categorise.security.UserPrincipal;
    import org.springframework.http.HttpStatus;
    import org.springframework.http.ResponseEntity;
    import org.springframework.security.core.annotation.AuthenticationPrincipal;
    import org.springframework.web.bind.annotation.GetMapping;
    import org.springframework.web.bind.annotation.RequestMapping;
    import org.springframework.web.bind.annotation.RestController;
    import org.springframework.web.server.ResponseStatusException;

    @RestController
    @RequestMapping("/user")
    public class UserController {

        private final UserService userService;

        public UserController(UserService userService) {
            this.userService = userService;
        }

        @GetMapping("/profile")
        public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
            if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
            return ResponseEntity.ok(new User(
                principal.getId(),
                principal.getName(),
                principal.getEmail()
            ));
        }
    }
