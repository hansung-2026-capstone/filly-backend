package net.coboogie.archive.dto;

/**
 * 아카이브 폴더 수정 요청 DTO.
 * null인 필드는 기존 값을 유지한다.
 *
 * @param name  변경할 폴더 이름 (선택)
 * @param color 변경할 폴더 색상 (선택)
 */
public record ArchiveFolderUpdateRequest(String name, String color) {
}
