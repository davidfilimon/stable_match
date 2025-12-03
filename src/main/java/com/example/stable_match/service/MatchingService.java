package com.example.stable_match.service;

import com.example.stable_match.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchingService {

    // Metoda solveRandom - nemodificată
    public StableMatchResponse solveRandom(StableMatchRequest request) {
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

        return new StableMatchResponse(matches, false);
    }


    public StableMatchResponse solveGaleShapley(StableMatchRequest request) {
        // Mapează cursurile și studenții pentru acces rapid
        Map<Long, CourseDto> courseMap = request.getCourses().stream()
                .collect(Collectors.toMap(CourseDto::getId, c -> c));

        // Mapează studenții pentru a găsi rapid DTO-ul pe baza ID-ului
        Map<Long, StudentDto> studentMap = request.getStudents().stream()
                .collect(Collectors.toMap(StudentDto::getId, s -> s));


        Map<Long, List<Long>> courseAssignments = new HashMap<>();
        request.getCourses().forEach(c -> courseAssignments.put(c.getId(), new ArrayList<>()));

        Queue<StudentDto> freeStudents = new LinkedList<>(request.getStudents());

        if (request.getStudents().isEmpty() || request.getCourses().isEmpty()) {
            return new StableMatchResponse(new ArrayList<>(), true);
        }

        Map<Long, Integer> studentProposalsCount = new HashMap<>();
        request.getStudents().forEach(s -> studentProposalsCount.put(s.getId(), 0));

        while (!freeStudents.isEmpty()) {
            StudentDto student = freeStudents.poll();

            int proposalIndex = studentProposalsCount.get(student.getId());

            if (proposalIndex >= student.getPreferences().size()) {
                continue;
            }

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

                // Logica de înlocuire: studentul nou vs. cel mai slab student existent
                if (isPreferred(course, student.getId(), worstStudentId)) {
                    currentStudents.remove(worstStudentId);
                    currentStudents.add(student.getId());

                    StudentDto worstStudentDto = studentMap.get(worstStudentId);

                    if (worstStudentDto != null) {
                        freeStudents.add(worstStudentDto);
                    }
                } else {
                    freeStudents.add(student); // Studentul propunător este respins
                }
            }
        }

        List<MatchPair> finalMatches = courseAssignments.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(sId -> new MatchPair(sId, entry.getKey())))
                .collect(Collectors.toList());

        return new StableMatchResponse(finalMatches, true);
    }

    // FIX: Am adăugat protecție împotriva listei de preferințe goale sau a studentului care nu este găsit
    private Long findWorstStudent(CourseDto course, List<Long> currentStudents) {
        if (course.getPreferences() == null || course.getPreferences().isEmpty()) {
            // Dacă cursul nu are preferințe (e o problemă de date), îl dăm afară pe primul
            return currentStudents.isEmpty() ? null : currentStudents.get(0);
        }

        Long worst = null;
        // Folosim un index mare pentru a găsi studentul cu rangul cel mai mic (cel mai mare index)
        int maxIndex = -1;
        List<Long> preferences = course.getPreferences();

        for (Long sId : currentStudents) {
            int index = preferences.indexOf(sId);

            // FIX: Dacă studentul nu figurează în lista de preferințe a cursului (index == -1),
            // îl considerăm pe ultimul loc (cel mai slab, Integer.MAX_VALUE)
            if (index == -1) {
                index = Integer.MAX_VALUE;
            }

            if (index > maxIndex) {
                maxIndex = index;
                worst = sId;
            }
        }
        return worst;
    }

    // FIX: Am adăugat protecție în isPreferred
    private boolean isPreferred(CourseDto course, Long studentA, Long studentB) {
        if (course.getPreferences() == null || course.getPreferences().isEmpty()) {
            // Dacă nu avem preferințe, studentul nou (A) este întotdeauna preferat (First Come, First Served)
            return true;
        }

        List<Long> preferences = course.getPreferences();
        int indexA = preferences.indexOf(studentA);
        int indexB = preferences.indexOf(studentB);

        // Dacă un student nu este în preferințe, îi dăm cel mai mic rang posibil (cel mai slab)
        if (indexA == -1) indexA = Integer.MAX_VALUE;
        if (indexB == -1) indexB = Integer.MAX_VALUE;

        // A este preferat dacă indexul său este mai mic (adică rang mai bun)
        return indexA < indexB;
    }
}