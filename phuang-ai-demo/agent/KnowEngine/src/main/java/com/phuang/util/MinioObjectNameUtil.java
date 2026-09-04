package com.phuang.util;

import com.phuang.model.exception.BusinessException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * MinIO 对象名称生成工具
 *
 * <p>用户上传的原始文件名可能包含中文、空格、路径分隔符等字符。如果直接把原始文件名
 * 拼接到 HTTP URL 中，部分外部服务会因为 URL 未编码而拒绝该地址。因此对象名只保留
 * URL 友好的 ASCII 字符，并通过日期目录和 UUID 避免同名文件互相覆盖。</p>
 */
public final class MinioObjectNameUtil {

    private static final Pattern UNSAFE_BASE_NAME_CHARACTERS = Pattern.compile("[^A-Za-z0-9]+");

    private static final Pattern EDGE_HYPHENS = Pattern.compile("(^-+|-+$)");

    private static final Pattern VALID_EXTENSION = Pattern.compile("[A-Za-z0-9]{1,10}");

    private static final int MAX_BASE_NAME_LENGTH = 60;

    private MinioObjectNameUtil() {
    }

    /**
     * 根据用户上传的原始文件名生成安全且唯一的 MinIO 对象名。
     *
     * <p>示例：</p>
     * <pre>
     * 从 Volta 到 Blackwell 看 Tensor Core 五代技术升级.pdf
     *   -> documents/2026/09/03/volta-blackwell-tensor-core-{uuid}.pdf
     * </pre>
     *
     * @param originalFilename 用户上传的原始文件名
     * @return 仅包含 ASCII 安全字符的 MinIO 对象名
     */
    public static String generateObjectName(String originalFilename) {
        String cleanFilename = extractFilename(originalFilename);
        int extensionSeparator = cleanFilename.lastIndexOf('.');
        if (extensionSeparator <= 0 || extensionSeparator == cleanFilename.length() - 1) {
            throw new BusinessException("上传文件缺少有效的扩展名");
        }

        String extension = cleanFilename.substring(extensionSeparator + 1);
        if (!VALID_EXTENSION.matcher(extension).matches()) {
            throw new BusinessException("上传文件扩展名不合法: " + extension);
        }

        String originalBaseName = cleanFilename.substring(0, extensionSeparator);
        String safeBaseName = toSafeBaseName(originalBaseName);
        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "");

        return "documents/" + safeBaseName + "-" + uniqueSuffix + "."
                + extension.toLowerCase(Locale.ROOT);
    }

    /**
     * 去除浏览器可能携带的本地路径，只保留真正的文件名。
     */
    private static String extractFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("上传文件名称不能为空");
        }

        String normalizedPath = originalFilename.trim().replace('\\', '/');
        String filename = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
        if (filename.isBlank()) {
            throw new BusinessException("上传文件名称不能为空");
        }
        return filename;
    }

    /**
     * 保留文件名中的英文和数字，其余连续字符统一替换为短横线。
     */
    private static String toSafeBaseName(String originalBaseName) {
        String normalized = Normalizer.normalize(originalBaseName, Normalizer.Form.NFKD);
        String safeBaseName = UNSAFE_BASE_NAME_CHARACTERS.matcher(normalized).replaceAll("-");
        safeBaseName = EDGE_HYPHENS.matcher(safeBaseName).replaceAll("");
        safeBaseName = safeBaseName.toLowerCase(Locale.ROOT);

        // 文件名只有中文或特殊字符时，使用通用前缀；原始名称仍可保存在文档标题等业务字段中。
        if (safeBaseName.isBlank()) {
            safeBaseName = "file";
        }
        if (safeBaseName.length() > MAX_BASE_NAME_LENGTH) {
            safeBaseName = safeBaseName.substring(0, MAX_BASE_NAME_LENGTH);
            safeBaseName = EDGE_HYPHENS.matcher(safeBaseName).replaceAll("");
        }
        return safeBaseName;
    }
}
