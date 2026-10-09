package com.myos.learning;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学习记录 —— 五段式（对应 PRD §6.2）。
 * 把「看过」变成「理解过、实践过、输出过」。
 */
@RestController
@RequestMapping("/api/v1/learning")
public class LearningController {

    private final LearningMapper mapper;

    public LearningController(LearningMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String systemCode) {
        Long uid = CurrentUser.id();
        return ApiResponse.ok(systemCode == null || systemCode.isBlank()
                ? mapper.findByUserId(uid)
                : mapper.findBySystem(uid, systemCode));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody CreateRequest req) {
        LearningRecord record = new LearningRecord(null, req.title(), req.category(), req.systemCode(),
                req.source(), req.content(), req.understanding(), req.practice(),
                req.conclusion(), req.durationMinutes(), null);
        mapper.insert(CurrentUser.id(), record);
        return ApiResponse.ok(record);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        mapper.delete(CurrentUser.id(), id);
        return ApiResponse.ok(true);
    }


    public record CreateRequest(@NotBlank String title, String category, String systemCode, String source,
                                String content, String understanding, String practice, String conclusion,
                                Integer durationMinutes) {}
}
