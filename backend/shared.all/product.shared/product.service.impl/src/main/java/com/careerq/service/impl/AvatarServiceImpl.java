package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.dao.UserDao;
import com.careerq.security.AuthUser;
import com.careerq.service.AvatarService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AvatarServiceImpl implements AvatarService {

    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Pattern SAFE_NAME = Pattern.compile("^[0-9]+-[0-9a-fA-F-]+\\.(png|jpg|jpeg|webp)$");

    private final UserDao userDao;
    private final Path baseDir;

    public AvatarServiceImpl(UserDao userDao, @Value("${careerq.avatar.dir:data/avatars}") String dir) {
        this.userDao = userDao;
        this.baseDir = Paths.get(dir).toAbsolutePath().normalize();
    }

    @Override
    public String store(AuthUser user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "请选择头像图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "头像不能超过 2MB");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "头像读取失败");
        }
        String ext = detectExtension(data);
        if (ext == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "仅支持 PNG / JPEG / WEBP 图片");
        }
        String filename = user.getId() + "-" + UUID.randomUUID() + "." + ext;
        try {
            Files.createDirectories(baseDir);
            Path target = baseDir.resolve(filename).normalize();
            if (!target.startsWith(baseDir)) {
                throw new BusinessException(ErrorCode.INVALID_PARAM, "非法文件名");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "头像保存失败");
        }
        String url = "/api/v1/avatars/" + filename;
        userDao.updateAvatar(user.getId(), url);
        return url;
    }

    @Override
    public AvatarFile load(String filename) {
        if (filename == null || !SAFE_NAME.matcher(filename).matches()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "头像不存在");
        }
        Path target = baseDir.resolve(filename).normalize();
        if (!target.startsWith(baseDir) || !Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "头像不存在");
        }
        try {
            return new AvatarFile(Files.readAllBytes(target), contentTypeOf(filename));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "头像不存在");
        }
    }

    /** 通过文件头判断真实类型，不信任客户端 Content-Type。 */
    private String detectExtension(byte[] data) {
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "png";
        }
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private String contentTypeOf(String filename) {
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }
}