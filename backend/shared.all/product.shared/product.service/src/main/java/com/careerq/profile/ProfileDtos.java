package com.careerq.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 可选完善资料：学历信息 + 实习/项目经历。
 */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record ExperienceView(
            Long id,
            String type,
            String title,
            String role,
            String startDate,
            String endDate,
            String description) {
    }

    public record ProfileView(
            Long id,
            String role,
            String nickname,
            String school,
            String major,
            String grade,
            String degree,
            String graduationYear,
            List<ExperienceView> experiences) {
    }

    public record ExperienceItem(
            @NotBlank(message = "经历类型不能为空")
            String type,
            @NotBlank(message = "公司/项目名称不能为空")
            @Size(max = 128, message = "公司/项目名称不能超过 128 字")
            String title,
            @Size(max = 64, message = "角色不能超过 64 字")
            String role,
            @Size(max = 16, message = "开始时间不能超过 16 字")
            String startDate,
            @Size(max = 16, message = "结束时间不能超过 16 字")
            String endDate,
            @Size(max = 1000, message = "描述不能超过 1000 字")
            String description) {
    }

    public record UpdateRequest(
            @Size(max = 128, message = "学校不能超过 128 字")
            String school,
            @Size(max = 128, message = "专业不能超过 128 字")
            String major,
            @Size(max = 32, message = "年级不能超过 32 字")
            String grade,
            @Size(max = 32, message = "学历不能超过 32 字")
            String degree,
            @Size(max = 16, message = "毕业年份不能超过 16 字")
            String graduationYear,
            @Valid
            List<ExperienceItem> experiences) {
    }

    /** 局部更新：字段为 null 表示不修改；experiences 为 null 时保留原有经历。 */
    public record PatchRequest(
            @Size(max = 64, message = "昵称不能超过 64 字")
            String nickname,
            @Size(max = 128, message = "学校不能超过 128 字")
            String school,
            @Size(max = 128, message = "专业不能超过 128 字")
            String major,
            @Size(max = 32, message = "年级不能超过 32 字")
            String grade,
            @Size(max = 32, message = "学历不能超过 32 字")
            String degree,
            @Size(max = 16, message = "毕业年份不能超过 16 字")
            String graduationYear,
            @Valid
            List<ExperienceItem> experiences) {
    }
}
