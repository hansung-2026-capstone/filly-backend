package net.coboogie.user.repository;

import net.coboogie.vo.UserVO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserVO, Long> {
    Optional<UserVO> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);
}
