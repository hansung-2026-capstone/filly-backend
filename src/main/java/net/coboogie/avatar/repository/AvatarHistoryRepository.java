package net.coboogie.avatar.repository;

import net.coboogie.vo.AvatarHistoryVO;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 아바타 생성 이력 JPA 레포지토리.
 */
public interface AvatarHistoryRepository extends JpaRepository<AvatarHistoryVO, Long> {
}
