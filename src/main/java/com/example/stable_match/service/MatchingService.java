package com.example.stable_match.service;

import com.example.stable_match.dto.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchingService {

    private final MeterRegistry meterRegistry;
    private final Logger logger = LoggerFactory.getLogger(MatchingService.class);

    private final Counter stableMatchCounter;
    private final Timer stableMatchTimer;

    public MatchingService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.stableMatchCounter = meterRegistry.counter("stable_match.invocations");
        this.stableMatchTimer = meterRegistry.timer("stable_match.response_time");
    }

    public StableMatchResponse solveRandom(StableMatchRequest request) {
        stableMatchCounter.increment();
        return stableMatchTimer.record(() -> {
            List<MatchPair> matches = new ArrayList<>();
            Map<Long, Integer> currentCapacities = request.getCourses().stream()
                    .collect(Collectors.toMap(CourseDto::getId, CourseDto::getCapacity));

            List<StudentDto> studentsToProcess = new ArrayList<>(request.getStudents());
            Collections.shuffle(studentsToProcess);

            for (StudentDto student : studentsToProcess) {
                for (Long courseId : student.getPreferences()) {
                    int cap = currentCapacities.getOrDefault(courseId, 0);
                    if (cap > 0) {
                        matches.add(new MatchPair(student.getId(), courseId));
                        currentCapacities.put(courseId, cap - 1);
                        break;
                    }
                }
            }

            logger.info("Random matching completed with {} matches", matches.size());
            return new StableMatchResponse(matches, false);
        });
    }

    public StableMatchResponse solveGaleShapley(StableMatchRequest request) {
        stableMatchCounter.increment();
        return stableMatchTimer.record(() -> {
            try {
                Map<Long, CourseDto> courseMap = request.getCourses().stream()
                        .collect(Collectors.toMap(CourseDto::getId, c -> c));

                Map<Long, StudentDto> studentMap = request.getStudents().stream()
                        .collect(Collectors.toMap(StudentDto::getId, s -> s));

                Map<Long, List<Long>> courseAssignments = new HashMap<>();
                request.getCourses().forEach(c -> courseAssignments.put(c.getId(), new ArrayList<>()));

                Queue<StudentDto> freeStudents = new LinkedList<>(request.getStudents());
                Map<Long, Integer> studentProposalsCount = new HashMap<>();
                request.getStudents().forEach(s -> studentProposalsCount.put(s.getId(), 0));

                if (request.getStudents().isEmpty() || request.getCourses().isEmpty()) {
                    return new StableMatchResponse(new ArrayList<>(), true);
                }

                while (!freeStudents.isEmpty()) {
                    StudentDto student = freeStudents.poll();
                    int proposalIndex = studentProposalsCount.get(student.getId());

                    if (proposalIndex >= student.getPreferences().size()) continue;

                    Long courseId = student.getPreferences().get(proposalIndex);
                    studentProposalsCount.put(student.getId(), proposalIndex + 1);
                    CourseDto course = courseMap.get(courseId);

                    if (course == null) {
                        freeStudents.add(student);
                        continue;
                    }

                    List<Long> currentStudents = courseAssignments.get(courseId);
                    if (currentStudents.size() < course.getCapacity()) {
                        currentStudents.add(student.getId());
                    } else {
                        Long worstStudentId = findWorstStudent(course, currentStudents);
                        if (worstStudentId == null) {
                            freeStudents.add(student);
                            continue;
                        }

                        if (isPreferred(course, student.getId(), worstStudentId)) {
                            currentStudents.remove(worstStudentId);
                            currentStudents.add(student.getId());
                            StudentDto worstStudentDto = studentMap.get(worstStudentId);
                            if (worstStudentDto != null) freeStudents.add(worstStudentDto);
                        } else {
                            freeStudents.add(student);
                        }
                    }
                }

                List<MatchPair> finalMatches = courseAssignments.entrySet().stream()
                        .flatMap(entry -> entry.getValue().stream()
                                .map(sId -> new MatchPair(sId, entry.getKey())))
                        .collect(Collectors.toList());

                logger.info("Gale-Shapley matching completed with {} matches", finalMatches.size());
                return new StableMatchResponse(finalMatches, true);
            } catch (Exception e) {
                logger.error("Error in Gale-Shapley algorithm", e);
                return new StableMatchResponse(new ArrayList<>(), false);
            }
        });
    }

    private Long findWorstStudent(CourseDto course, List<Long> currentStudents) {
        if (course.getPreferences() == null || course.getPreferences().isEmpty()) {
            return currentStudents.isEmpty() ? null : currentStudents.get(0);
        }

        Long worst = null;
        int maxIndex = -1;
        List<Long> preferences = course.getPreferences();

        for (Long sId : currentStudents) {
            int index = preferences.indexOf(sId);
            if (index == -1) index = Integer.MAX_VALUE;
            if (index > maxIndex) {
                maxIndex = index;
                worst = sId;
            }
        }
        return worst;
    }

    private boolean isPreferred(CourseDto course, Long studentA, Long studentB) {
        if (course.getPreferences() == null || course.getPreferences().isEmpty()) return true;

        List<Long> preferences = course.getPreferences();
        int indexA = preferences.indexOf(studentA);
        int indexB = preferences.indexOf(studentB);
        if (indexA == -1) indexA = Integer.MAX_VALUE;
        if (indexB == -1) indexB = Integer.MAX_VALUE;

        return indexA < indexB;
    }
}
