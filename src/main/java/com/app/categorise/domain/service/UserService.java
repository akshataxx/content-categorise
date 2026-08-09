package com.app.categorise.domain.service;

import com.app.categorise.data.entity.UserEntity;
import com.app.categorise.data.repository.DeviceRepository;
import com.app.categorise.data.repository.RefreshTokenRepository;
import com.app.categorise.data.repository.TranscriptionJobRepository;
import com.app.categorise.data.repository.UntranscribedLinkRepository;
import com.app.categorise.data.repository.UserRateLimitRepository;
import com.app.categorise.data.repository.UserRateLimitTrackingRepository;
import com.app.categorise.data.repository.UserRepository;
import com.app.categorise.data.repository.UserSubcategoryRepository;
import com.app.categorise.data.repository.UserSubscriptionRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.security.UserPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserTranscriptRepository userTranscriptRepository;
    private final UserSubcategoryRepository userSubcategoryRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final TranscriptionJobRepository transcriptionJobRepository;
    private final UntranscribedLinkRepository untranscribedLinkRepository;
    private final DeviceRepository deviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRateLimitRepository userRateLimitRepository;
    private final UserRateLimitTrackingRepository userRateLimitTrackingRepository;

    public UserService(
            UserRepository userRepository,
            UserTranscriptRepository userTranscriptRepository,
            UserSubcategoryRepository userSubcategoryRepository,
            UserSubscriptionRepository userSubscriptionRepository,
            TranscriptionJobRepository transcriptionJobRepository,
            UntranscribedLinkRepository untranscribedLinkRepository,
            DeviceRepository deviceRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserRateLimitRepository userRateLimitRepository,
            UserRateLimitTrackingRepository userRateLimitTrackingRepository) {
        this.userRepository = userRepository;
        this.userTranscriptRepository = userTranscriptRepository;
        this.userSubcategoryRepository = userSubcategoryRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.transcriptionJobRepository = transcriptionJobRepository;
        this.untranscribedLinkRepository = untranscribedLinkRepository;
        this.deviceRepository = deviceRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRateLimitRepository = userRateLimitRepository;
        this.userRateLimitTrackingRepository = userRateLimitTrackingRepository;
    }

    public Optional<UserEntity> findById(UUID id) {
        return userRepository.findById(id);
    }

    @Transactional
    public UserDetails loadUserById(UUID id) {
        UserEntity user = userRepository.findById(id).orElseThrow(
                () -> new UsernameNotFoundException("User not found with id : " + id)
        );

        return UserPrincipal.create(user);
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found with email : " + email)
                );

        return UserPrincipal.create(user);
    }

    public Optional<UserEntity> findBySub(String sub) {
        return userRepository.findBySub(sub);
    }

    public Optional<UserEntity> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public UserEntity saveUser(UserEntity user) {
        return userRepository.save(user);
    }

    public boolean existsByEmail(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    /**
     * Permanently delete a user account and all associated user-scoped data.
     *
     * <p>This is irreversible and is invoked when a user requests account
     * deletion (App Store guideline 5.1.1(v)). Shared base transcripts are not
     * removed - only the user's own associations and personal records. The whole
     * operation runs in a single transaction so a failure leaves no partial state.
     *
     * @param userId the ID of the user to delete
     * @throws UsernameNotFoundException if no user exists with the given ID
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new UsernameNotFoundException("User not found with id : " + userId);
        }

        // Remove user-scoped data first to satisfy any FK constraints.
        userTranscriptRepository.deleteByUserId(userId);
        userSubcategoryRepository.deleteByUserId(userId);
        userSubscriptionRepository.deleteByUserId(userId);
        transcriptionJobRepository.deleteByUserId(userId);
        untranscribedLinkRepository.deleteByUserId(userId);
        deviceRepository.deleteByUserId(userId);
        refreshTokenRepository.deleteByUserId(userId);
        userRateLimitRepository.deleteByUserId(userId);
        userRateLimitTrackingRepository.deleteByUserId(userId);

        // Finally remove the user record itself.
        userRepository.deleteById(userId);
    }
}
