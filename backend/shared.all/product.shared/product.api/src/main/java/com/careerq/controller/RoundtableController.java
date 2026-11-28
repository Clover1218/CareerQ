package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.roundtable.RoundtableDtos.Featured;
import com.careerq.roundtable.RoundtableDtos.Roundtable;
import com.careerq.roundtable.RoundtableDtos.TopicChoice;
import com.careerq.security.AuthUsers;
import com.careerq.service.RoundtableService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roundtable")
public class RoundtableController {

    private final RoundtableService roundtableService;

    public RoundtableController(RoundtableService roundtableService) {
        this.roundtableService = roundtableService;
    }

    @GetMapping
    public ApiResponse<Roundtable> current(Authentication authentication) {
        return ApiResponse.ok(roundtableService.current(AuthUsers.optional(authentication)));
    }

    @GetMapping("/featured")
    public ApiResponse<Featured> featured() {
        return ApiResponse.ok(roundtableService.featured());
    }

    @PostMapping("/nominations")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<Roundtable> nominate(Authentication authentication, @Valid @RequestBody TopicChoice choice) {
        return ApiResponse.ok(roundtableService.nominate(AuthUsers.require(authentication), choice));
    }

    @PutMapping("/vote")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<Roundtable> vote(Authentication authentication, @Valid @RequestBody TopicChoice choice) {
        return ApiResponse.ok(roundtableService.vote(AuthUsers.require(authentication), choice));
    }
}
