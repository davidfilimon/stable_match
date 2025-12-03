package com.example.stable_match.dto;

import lombok.Data;
import java.util.List;

@Data
public class StudentDto {
    private Long id;
    private String name;
    private List<Long> preferences;
}