package net.coboogie.user.repository;

import jakarta.persistence.LockModeType;
import net.coboogie.vo.UserVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * {@link UserVO} 엔티티를 조회하고 저장하는 JPA Repository.
 */
public interface UserRepository extends JpaRepository<UserVO, Long> {
    /**
     * OAuth 제공자와 제공자 사용자 ID로 사용자를 조회한다.
     *
     * @param oauthProvider OAuth 제공자
     * @param oauthId 제공자 사용자 ID
     * @return 사용자
     */
    Optional<UserVO> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

    /**
     * 사용자 단위 상태 전이를 직렬화하기 위해 쓰기 잠금으로 사용자를 조회한다.
     *
     * @param id 사용자 ID
     * @return 잠금이 걸린 사용자
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserVO u where u.id = :id")
    Optional<UserVO> findByIdForUpdate(@Param("id") Long id);
}
