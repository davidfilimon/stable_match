package com.example.stable_match.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StableMatchResponse {
    private List<MatchPair> assignments;
    private boolean isStable;
}