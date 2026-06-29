package com.wellnessmate.auth.repository;

import com.wellnessmate.auth.domain.UserAccount;
import com.wellnessmate.auth.domain.UserRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** User account persistence boundary. @author TODO(team member) */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
  Optional<UserAccount> findByUsernameIgnoreCase(String username);
  Optional<UserAccount> findByEmailIgnoreCase(String email);
  Optional<UserAccount> findFirstByRoleOrderByIdAsc(UserRole role);
  boolean existsByUsernameIgnoreCase(String username);
  boolean existsByEmailIgnoreCase(String email);
}
