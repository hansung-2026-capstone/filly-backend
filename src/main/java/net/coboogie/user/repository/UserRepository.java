package net.coboogie.user.repository;

import net.coboogie.fillybackend.vo.UserVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserVO, Long> {
    Optional<UserVO> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);
}
