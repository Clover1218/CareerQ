package com.careerq.service;

import com.careerq.security.AuthUser;
import org.springframework.web.multipart.MultipartFile;

/**
 * 头像上传与读取。
 * 安全约束：只接受 png/jpeg/webp 光栅图片，限制大小，校验文件魔数，文件名由服务端生成；
 * 不接受用户提供的外部 URL（避免跟踪像素、隐私泄露与不可控内容）。
 */
public interface AvatarService {

    /** 保存头像并返回可访问的站内相对路径（如 /api/v1/avatars/1-xxx.png）。 */
    String store(AuthUser user, MultipartFile file);

    /** 按服务端生成的文件名读取头像。 */
    AvatarFile load(String filename);

    record AvatarFile(byte[] data, String contentType) {
    }
}