package com.b2.prj02.service.User;

import com.b2.prj02.Exception.NotFoundException;
import com.b2.prj02.dto.request.UserDeleteRequestDTO;
import com.b2.prj02.dto.request.UserLoginRequestDTO;
import com.b2.prj02.dto.request.UserSignupRequestDTO;
import com.b2.prj02.entity.User;

import com.b2.prj02.role.UserStatus;
import com.b2.prj02.repository.UserRepository;
import com.b2.prj02.service.Image.S3Service;
import com.b2.prj02.service.jwt.JwtTokenProvider;
import com.b2.prj02.service.jwt.TokenBlacklist;
import io.jsonwebtoken.MalformedJwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    private final LockedUser lockedUser;
    private final S3Service s3Service;

    public ResponseEntity<?> signup(UserSignupRequestDTO user) {
        if (checkEmail(user.getEmail())) {
            User newUser = User.builder()
                    .email(user.getEmail())
                    .password(passwordEncoder.encode(user.getPassword()))
                    .address(user.getAddress())
                    .gender(user.getGender())
                    .nickName(user.getNickName())
                    .filePath(user.getFilePath())
                    .stack(0)
                    .build();

            String status = user.getStatus();

            switch (status) {
                case "USER":
                    newUser.updateStatus(UserStatus.USER);
                    userRepository.save(newUser);
                    return ResponseEntity.status(200).body(newUser.getNickName() + " 님 회원 가입을 축하드립니다.");

                case "SELLER":
                    newUser.updateStatus(UserStatus.SELLER);
                    userRepository.save(newUser);
                    return ResponseEntity.status(200).body(newUser.getNickName() + " 님 회원 가입을 축하드립니다.");

                default:
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("없는 Status입니다.");
            }
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("이미 가입된 정보입니다.");
    }


    public ResponseEntity<?> login(UserLoginRequestDTO user) {
        Optional<User> loginUser = userRepository.findByEmail(user.getEmail());

        if (loginUser.isEmpty())
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("이메일을 다시 확인해주세요.");


        if (loginUser.get().getStack() >= 5)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("5회 이상 오류로 인해 접속이 1분간 불가 합니다");


        if (!passwordEncoder.matches(user.getPassword(), loginUser.get().getPassword())) {
            lockedUser.addToFailedStack(loginUser.get());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("비밀번호를 다시 확인해주세요.");
        }

        loginUser.get().resetStack();
        userRepository.save(loginUser.get());

        String newToken = jwtTokenProvider.createToken(loginUser.get(), loginUser.get().getStatus());

        if (jwtTokenProvider.findStatusBytoken(newToken).equals("DELETED")) {

            TokenBlacklist.addToBlacklist(newToken);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("회원 탈퇴한 유저입니다.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(newToken);

        Map<String, String> response = new HashMap<>();
        response.put("email", loginUser.get().getEmail());
        response.put("address", loginUser.get().getAddress());
        response.put("staus", loginUser.get().getStatus().name());
        response.put("nick_name", loginUser.get().getNickName());
        response.put("file_path", loginUser.get().getFilePath());

        return ResponseEntity.status(200).headers(headers).body(response);
    }


    public ResponseEntity<?> logout(String token) {
        try {
            String userEmail = jwtTokenProvider.findEmailBytoken(token);
            if (userRepository.findByEmail(userEmail).isEmpty())
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("로그아웃에 실패하셨습니다.");
            TokenBlacklist.addToBlacklist(token);
            return ResponseEntity.status(200).body("이용해 주셔서 감사합니다.");
        } catch (Exception e) {
            e.getMessage();
            throw new MalformedJwtException("로그아웃에 실패하셨습니다.");
        }
    }


    public ResponseEntity<?> deleteUser(String token, UserDeleteRequestDTO deleteUser) {
        String email = jwtTokenProvider.findEmailBytoken(token);
        Optional<User> storedUser = userRepository.findByEmail(email);

        User updatedUser = storedUser.map(user -> user.updateStatus(UserStatus.DELETED))
                .orElseThrow(() -> new RuntimeException("없는 유저입니다."));


        if (email.equals(deleteUser.getEmail()) && passwordEncoder.matches(deleteUser.getPassword(), storedUser.get().getPassword())) {
            userRepository.save(updatedUser);
            return ResponseEntity.status(200).body("회원 탈퇴되셨습니다.");
        } else return ResponseEntity.status(HttpStatus.FORBIDDEN).body("이메일 또는 비밀번호가 틀렸습니다.");
    }

    public String saveImage(MultipartFile file) throws IOException {
        return s3Service.uploadFileAndGetUrl(file);
    }

    public String saveImageToToken(MultipartFile file, User user) throws IOException {
        String url = s3Service.uploadFileAndGetUrl(file);
        user.setFilePath(url);
        userRepository.save(user);

        return url;
    }

    public User checkUser(String token) {
        String email = jwtTokenProvider.findEmailBytoken(token);
        Optional<User> user = userRepository.findByEmail(email);

        if (user.isEmpty())
            throw new NotFoundException("로그인을 다시 해주세요.");

        return user.get();
    }

    public Boolean checkEmail(String email) {
        return userRepository.findByEmail(email).isEmpty() || userRepository.findByEmail(email).get().getStatus().equals(UserStatus.DELETED);
    }
}
