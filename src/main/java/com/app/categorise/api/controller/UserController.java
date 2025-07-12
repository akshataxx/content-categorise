    package com.app.categorise.api.controller;

    import com.app.categorise.data.entity.UserEntity;
    import com.app.categorise.domain.model.User;
    import com.app.categorise.domain.service.UserService;
    import org.springframework.http.HttpStatus;
    import org.springframework.http.ResponseEntity;
    import org.springframework.security.core.annotation.AuthenticationPrincipal;
    import org.springframework.security.oauth2.core.user.OAuth2User;
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
        public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal OAuth2User principal) {
            if (principal == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
            }
            System.out.println("Principal: " + principal);
            System.out.println("Attributes: " + principal.getAttributes());

            String sub = principal.getAttribute("sub");
            if (sub == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing 'sub' in OAuth2 principal");
            }

            UserEntity user = userService.findBySub(sub)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            System.out.println("User: " + user);

            return ResponseEntity.ok(new User(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPictureUrl()
            ));
        }
    }
