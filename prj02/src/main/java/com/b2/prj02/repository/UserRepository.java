package com.b2.prj02.repository;

import com.b2.prj02.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.Optional;
//@CrossOrigin(origins = "http://localhost:8080",allowedHeaders = "*")
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);


    @Query("SELECT u FROM User u WHERE u.email = :email")
    UserDetails findUserDetailByEmail(@Param("email") String email);
}
