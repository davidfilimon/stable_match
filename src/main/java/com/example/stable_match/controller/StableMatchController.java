package com.example.stable_match.controller;

import com.example.stable_match.dto.StableMatchRequest;
import com.example.stable_match.dto.StableMatchResponse;
import com.example.stable_match.service.MatchingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/matching")
public class StableMatchController {

    @Autowired
    private MatchingService matchingService;

    @PostMapping("/solve")
    public StableMatchResponse solve(
            @RequestBody StableMatchRequest request,
            @RequestParam(defaultValue = "random") String algorithm) {

        System.out.println("--- StableMatch: Processing request for " + request.getStudents().size() + " students using " + algorithm + " strategy.");

        if ("gale-shapley".equalsIgnoreCase(algorithm)) {
            return matchingService.solveGaleShapley(request);
        } else {
            return matchingService.solveRandom(request);
        }
    }
}