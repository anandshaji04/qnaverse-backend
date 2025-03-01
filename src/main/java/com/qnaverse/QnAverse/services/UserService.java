package com.qnaverse.QnAverse.services;

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.qnaverse.QnAverse.models.Question;
import com.qnaverse.QnAverse.models.User;
import com.qnaverse.QnAverse.repositories.FollowRepository;
import com.qnaverse.QnAverse.repositories.QuestionRepository;
import com.qnaverse.QnAverse.repositories.UserRepository;
import com.qnaverse.QnAverse.utils.FileStorageUtil;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final BlockingService blockingService;
    private final QuestionRepository questionRepository;
    private final FileStorageUtil fileStorageUtil;

    public UserService(UserRepository userRepository,
                       FollowRepository followRepository,
                       BlockingService blockingService,
                       QuestionRepository questionRepository,
                       FileStorageUtil fileStorageUtil) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.blockingService = blockingService;
        this.questionRepository = questionRepository;
        this.fileStorageUtil = fileStorageUtil;
    }

    // Get User Profile (with follower and following counts)
    public ResponseEntity<?> getUserProfile(String username) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User user = userOptional.get();

        long followerCount = followRepository.countByFollowing(user);
        long followingCount = followRepository.countByFollower(user);

        ProfileResponse response = new ProfileResponse(user, followerCount, followingCount);
        return ResponseEntity.ok(response);
    }

    // Update User Profile (excluding profile picture)
    public ResponseEntity<?> updateUserProfile(String username, User updatedUser) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User user = userOptional.get();
        user.setBio(updatedUser.getBio());
        user.setInstagramUrl(updatedUser.getInstagramUrl());
        user.setGithubUrl(updatedUser.getGithubUrl());
        user.setLinkedinUrl(updatedUser.getLinkedinUrl());
        userRepository.save(user);
        return ResponseEntity.ok("Profile updated successfully");
    }

    // Update Profile Picture with Cloudinary support
    public ResponseEntity<?> updateProfilePicture(String username, MultipartFile file) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid file");
        }

        User user = userOptional.get();

        // Delete old profile picture from Cloudinary if exists
        if (user.getProfilePicture() != null && !user.getProfilePicture().isEmpty()) {
            fileStorageUtil.deleteFromCloudinary(user.getProfilePicture());  // Delete old picture from Cloudinary
        }

        // Save the new profile picture to Cloudinary
        String mediaUrl = fileStorageUtil.saveToCloudinary(file, "profile_pictures");
        user.setProfilePicture(mediaUrl);  // Update the profile picture URL
        userRepository.save(user);  // Save the updated user object with the new profile picture

        return ResponseEntity.ok("Profile picture updated successfully");
    }

    // Get all questions posted by a user (with block check)
    public ResponseEntity<?> getUserQuestions(String username, String viewerUsername) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User theUser = userOpt.get();

        if (viewerUsername != null && !viewerUsername.isBlank()) {
            Optional<User> viewerOpt = userRepository.findByUsername(viewerUsername);
            if (viewerOpt.isPresent()) {
                User viewer = viewerOpt.get();
                // Check if the viewer is blocked by the user or vice versa
                if (blockingService.isBlockedEitherWay(viewer, theUser)) {
                    return ResponseEntity.ok(List.of()); // Return empty if blocked
                }
            }
        }

        // Fetch questions posted by the user
        List<Question> all = questionRepository.findByUserIdsApproved(List.of(theUser.getId()));
        return ResponseEntity.ok(all); // Return list of approved questions
    }

    // ProfileResponse is a helper class to format the profile response
    private static class ProfileResponse {
        public Long id;
        public String username;
        public String email;
        public String bio;
        public String profilePicture;
        public String instagramUrl;
        public String githubUrl;
        public String linkedinUrl;
        public String role;
        public long followerCount;
        public long followingCount;

        public ProfileResponse(User user, long followerCount, long followingCount) {
            this.id = user.getId();
            this.username = user.getUsername();
            this.email = user.getEmail();
            this.bio = user.getBio();
            this.profilePicture = user.getProfilePicture();
            this.instagramUrl = user.getInstagramUrl();
            this.githubUrl = user.getGithubUrl();
            this.linkedinUrl = user.getLinkedinUrl();
            this.role = user.getRole().name();
            this.followerCount = followerCount;
            this.followingCount = followingCount;
        }
    }
}
