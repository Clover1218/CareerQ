import { ref } from "vue";
import { useDialog, useMessage } from "naive-ui";
import { api, type Answer } from "@/api";

type OwnAnswer = Pick<Answer, "id" | "status" | "followupId" | "removalSource">;
const WITHDRAWABLE = new Set(["published", "pending_machine", "pending_human"]);

export function canWithdrawAnswer(answer: OwnAnswer): boolean {
  return WITHDRAWABLE.has(answer.status);
}

/** 供 HR 工作台和详情共用；内容归属仍由后端从会话验证。 */
export function useAnswerWithdrawal(reload: () => Promise<unknown>) {
  const dialog = useDialog();
  const message = useMessage();
  const withdrawingAnswerId = ref<number | null>(null);

  function requestWithdrawal(answer: OwnAnswer) {
    if (withdrawingAnswerId.value !== null || !canWithdrawAnswer(answer)) return;
    const label = answer.followupId == null ? "回答" : "追问回复";
    dialog.warning({
      title: `撤销这条${label}`,
      content: "撤销后这条内容将不再公开，已有追问与回复按各自状态保留。确定撤销吗？",
      positiveText: "确定撤销",
      negativeText: "取消",
      onPositiveClick: async () => {
        if (withdrawingAnswerId.value !== null) return false;
        withdrawingAnswerId.value = answer.id;
        try {
          const withdrawn = await api.withdrawAnswer(answer.id);
          answer.status = withdrawn.status;
          answer.removalSource = withdrawn.removalSource;
          try {
            await reload();
            message.success(`这条${label}已撤销。`);
          } catch {
            message.warning("已撤销，请刷新页面查看最新状态。");
          }
          return true;
        } catch (cause) {
          message.error(cause instanceof Error ? cause.message : "撤销失败，请稍后重试。");
          return false;
        } finally {
          withdrawingAnswerId.value = null;
        }
      },
    });
  }

  return { requestWithdrawal, withdrawingAnswerId };
}
