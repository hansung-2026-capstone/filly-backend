package net.coboogie.archive.dto;

/**
 * 아카이브 폴더 생성 요청 DTO.
 *
 * @param name  폴더 이름 (최대 50자)
 * @param color 폴더 색상 (예: pink, mint, yellow, blue, purple, gray)
 */
public record ArchiveFolderCreateRequest(String name, String color) {
}
