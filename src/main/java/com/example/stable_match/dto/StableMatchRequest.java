package com.example.stable_match.dto;

import lombok.Data;
import java.util.List;

@Data
public class StableMatchRequest {
    private List<StudentDto> students;
    private List<CourseDto> courses;
}