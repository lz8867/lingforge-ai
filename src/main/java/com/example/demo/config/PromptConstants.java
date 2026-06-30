package com.example.demo.config;

import java.util.HashMap;
import java.util.Map;

public final class PromptConstants {

    public static final Map<String, Object> TEMPLATES = new HashMap<>();

    static {
        TEMPLATES.put("1", createTemplate("1", "代码审查助手", "帮助审查代码并提供改进建议", "work",
                "你是一个专业的代码审查专家。请审查以下代码，并提供详细的改进建议：\n\n代码语言：{{language}}\n\n代码内容：\n```\n{{code}}\n```\n\n请从以下几个方面进行审查：\n1. 代码质量和可读性\n2. 性能优化建议\n3. 潜在的bug和错误\n4. 最佳实践建议\n5. 安全性考虑",
                Map.of("language", "编程语言", "code", "代码内容")));

        TEMPLATES.put("2", createTemplate("2", "文档生成器", "根据代码自动生成文档", "work",
                "你是一个技术文档编写专家。请根据以下代码生成详细的技术文档：\n\n项目名称：{{projectName}}\n代码语言：{{language}}\n\n代码内容：\n```\n{{code}}\n```\n\n请生成包含以下内容的文档：\n1. 功能概述\n2. API接口说明\n3. 参数说明\n4. 返回值说明\n5. 使用示例\n6. 注意事项",
                Map.of("projectName", "项目名称", "language", "编程语言", "code", "代码内容")));

        TEMPLATES.put("3", createTemplate("3", "翻译助手", "多语言翻译工具", "creative",
                "你是一个专业的翻译专家。请将以下内容从{{sourceLanguage}}翻译成{{targetLanguage}}：\n\n原文：\n{{text}}\n\n要求：\n1. 保持原文的语气和风格\n2. 确保翻译的准确性和流畅性\n3. 对于专业术语，保留原文或提供注释",
                Map.of("sourceLanguage", "源语言", "targetLanguage", "目标语言", "text", "待翻译文本")));

        TEMPLATES.put("4", createTemplate("4", "学习计划生成器", "根据学习目标生成学习计划", "creative",
                "你是一个专业的教育规划专家。请根据以下信息为学习者制定详细的学习计划：\n\n学习主题：{{topic}}\n当前水平：{{currentLevel}}\n学习目标：{{goal}}\n可用时间：{{timeAvailable}}\n\n请制定一个包含以下内容的学习计划：\n1. 学习阶段划分\n2. 每个阶段的学习目标\n3. 推荐的学习资源\n4. 学习方法和建议\n5. 进度检查点\n6. 预期完成时间",
                Map.of("topic", "学习主题", "currentLevel", "当前水平", "goal", "学习目标", "timeAvailable", "可用时间")));

        TEMPLATES.put("5", createTemplate("5", "创意写作助手", "帮助进行创意写作", "creative",
                "你是一个创意写作专家。请根据以下要求进行创作：\n\n写作类型：{{type}}\n主题：{{theme}}\n风格：{{style}}\n篇幅：{{length}}\n\n要求：\n1. 内容要有创意和吸引力\n2. 语言流畅，表达清晰\n3. 符合指定的风格要求\n4. 结构完整，逻辑清晰",
                Map.of("type", "写作类型", "theme", "主题", "style", "风格", "length", "篇幅")));

        TEMPLATES.put("6", createTemplate("6", "高情商回复助手", "提供高情商的沟通建议和回复", "eq",
                "你是一个情商极高的沟通专家，擅长用温暖、理解、尊重的方式回应各种社交场景。请根据以下信息提供高情商的回复建议：\n\n场景类型：{{scenario}}\n对方说的话：{{message}}\n你的角色：{{role}}\n回复目标：{{goal}}\n\n请提供：\n1. **共情理解**：先表达对对方感受的理解和认可\n2. **高情商回复**：提供3-5个不同风格的回复选项，每个选项都体现高情商特质\n   - 温暖关怀型\n   - 专业得体型\n   - 幽默轻松型\n   - 深度思考型\n   - 简洁有力型\n3. **沟通技巧**：说明这个回复中运用了哪些高情商沟通技巧\n4. **注意事项**：提醒需要避免的雷区和注意事项\n\n要求：\n- 回复要真诚自然，不生硬\n- 体现同理心和尊重\n- 语言要温暖而有力量\n- 根据不同场景调整语气和表达方式",
                Map.of("scenario", "场景类型（如：工作沟通、朋友聊天、情侣对话、客户服务、面试等）",
                       "message", "对方说的话",
                       "role", "你的角色（如：同事、朋友、伴侣、客服、面试官等）",
                       "goal", "回复目标（如：安慰、鼓励、拒绝、道歉、感谢、说服等）")));

        TEMPLATES.put("7", createTemplate("7", "职场沟通专家", "处理职场中的各种沟通场景", "work",
                "你是一个职场沟通专家，擅长处理各种复杂的职场沟通场景。请根据以下情况提供专业建议：\n\n沟通场景：{{scenario}}\n涉及人员：{{people}}\n问题/情况：{{situation}}\n你的诉求：{{request}}\n\n请提供：\n1. **场景分析**：分析这个沟通场景的关键点和潜在挑战\n2. **沟通策略**：制定合适的沟通策略和步骤\n3. **话术建议**：提供具体的话术和表达方式\n4. **情绪管理**：如何管理自己和他人的情绪\n5. **备选方案**：如果情况不如预期，如何应对\n\n要求：\n- 保持专业和礼貌\n- 注重事实和逻辑\n- 考虑各方利益\n- 提供可执行的建议",
                Map.of("scenario", "沟通场景（如：向上汇报、跨部门协作、冲突处理、绩效面谈等）",
                       "people", "涉及人员",
                       "situation", "问题/情况描述",
                       "request", "你的诉求")));

        TEMPLATES.put("8", createTemplate("8", "情感支持助手", "为朋友或家人提供情感支持", "eq",
                "你是一个温暖体贴的情感支持专家，擅长用同理心和温暖的话语安慰和鼓励他人。请根据以下情况提供情感支持建议：\n\n对方当前状态：{{mood}}\n发生的事情：{{event}}\n你们的关系：{{relationship}}\n你希望达到的效果：{{effect}}\n\n请提供：\n1. **情感共鸣**：表达理解和共情的话语\n2. **安慰话语**：3-5句温暖有力的安慰话术\n3. **行动建议**：可以采取的具体行动来支持对方\n4. **避雷提醒**：哪些话或行为可能会让对方更难受\n5. **后续关怀**：如何持续关注和支持对方\n\n要求：\n- 语气温暖真诚\n- 避免说教和评判\n- 尊重对方的感受\n- 提供实际可行的建议",
                Map.of("mood", "对方当前状态（如：难过、焦虑、沮丧、失落、愤怒等）",
                       "event", "发生的事情",
                       "relationship", "你们的关系",
                       "effect", "你希望达到的效果（如：让对方感到被理解、给予力量、帮助缓解情绪等）")));
    }

    private static Map<String, Object> createTemplate(String id, String name, String description, String category, String template, Map<String, String> variables) {
        Map<String, Object> templateMap = new HashMap<>();
        templateMap.put("id", id);
        templateMap.put("name", name);
        templateMap.put("description", description);
        templateMap.put("category", category);
        templateMap.put("template", template);
        templateMap.put("variables", variables);
        return templateMap;
    }

    public static Map<String, Object> getTemplates() {
        return new HashMap<>(TEMPLATES);
    }

    public static Map<String, Object> getTemplate(String id) {
        return new HashMap<>((Map<String, Object>) TEMPLATES.get(id));
    }

    private PromptConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
