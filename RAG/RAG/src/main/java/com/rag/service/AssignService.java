package com.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.entity.Ticket;
import com.rag.entity.User;
import com.rag.mapper.TicketMapper;
import com.rag.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 工单自动分配：按类别匹配技能的管理员，同技能中选当前未完结工单最少的人（负载均衡）。
 * 匹配规则：
 *   1. 技能标签包含工单类别 的管理员优先；
 *   2. 无匹配时回退到 skills 为空（兜底全能）的管理员；
 *   3. 完全没有可用管理员时返回 null（工单保持 OPEN 待人工认领）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignService {

    private final UserMapper userMapper;
    private final TicketMapper ticketMapper;

    /** 选出处理人姓名；无人可用返回 null */
    public String pickAssignee(String category) {
        List<User> admins = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "ADMIN"));
        if (admins.isEmpty()) {
            return null;
        }

        List<User> matched = admins.stream()
                .filter(u -> hasSkill(u, category))
                .toList();
        List<User> fallback = admins.stream()
                .filter(u -> u.getSkills() == null || u.getSkills().isBlank())
                .toList();

        List<User> candidates = !matched.isEmpty() ? matched : fallback;
        if (candidates.isEmpty()) {
            log.info("自动分配：类别[{}]无匹配技能且无兜底管理员，工单等待人工认领", category);
            return null;
        }

        User best = candidates.stream()
                .min(Comparator.comparingInt(this::activeLoad))
                .orElse(candidates.get(0));
        log.info("自动分配：类别[{}] → {}（当前未完结工单 {} 单）",
                category, best.getName(), activeLoad(best));
        return best.getName();
    }

    /** 该管理员名下未完结（已提交/处理中）的工单数 */
    private int activeLoad(User admin) {
        Long count = ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getAssignee, admin.getName())
                .in(Ticket::getStatus, Ticket.OPEN, Ticket.PROCESSING));
        return count == null ? 0 : count.intValue();
    }

    private boolean hasSkill(User admin, String category) {
        if (admin.getSkills() == null || category == null) {
            return false;
        }
        for (String skill : admin.getSkills().split("[,，]")) {
            if (skill.trim().equals(category.trim())) {
                return true;
            }
        }
        return false;
    }
}
