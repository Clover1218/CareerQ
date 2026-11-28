export const clinicTopics = [
  {
    id: "career", tag: "职业规划", name: "职业选择",
    hint: "想选行业、岗位或比较 Offer",
    example: "后端开发和测试，我更适合哪个？",
  },
  {
    id: "resume", tag: "简历", name: "简历优化",
    hint: "想改简历或写好项目经历",
    example: "只有课程项目，简历怎么写？",
  },
  {
    id: "interview", tag: "面试准备", name: "面试准备",
    hint: "想练面试或复盘面试表现",
    example: "面试官问我对项目的贡献，该怎么答？",
  },
  {
    id: "intern", tag: "实习", name: "实习求职",
    hint: "想找实习、了解投递或转正",
    example: "暑期实习什么时候开始投？",
  },
];

export type ClinicTopic = (typeof clinicTopics)[number];

export function clinicTopic(id: unknown) {
  return clinicTopics.find(topic => topic.id === id);
}

export const clinicRoomsLocation = { name: "clinic-home", hash: "#clinic-rooms" };

export function clinicRoomLocation(topic: ClinicTopic) {
  return { name: "clinic-topic", params: { topic: topic.id } };
}

export function clinicAskLocation(topic: ClinicTopic) {
  return { path: "/ask", query: { topic: topic.id } };
}

export function supplementalClinicTags(tags: string[]) {
  return tags.filter(tag => !clinicTopics.some(topic => topic.tag === tag));
}
