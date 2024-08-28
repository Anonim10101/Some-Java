package student;

import info.kgeorgiy.java.advanced.student.*;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StudentDB {
    private static final Comparator<Student> nameComparator =
            Comparator.comparing(Student::getLastName).
                    thenComparing(Student::getFirstName).
                    thenComparing(Student::getId, Comparator.reverseOrder());

    public List<Group> getGroupsByName(final Collection<Student> students) {
        return getGroupBy(students, this::sortStudentsByName);
    }

    public List<Group> getGroupsById(final Collection<Student> students) {
        return getGroupBy(students, this::sortStudentsById);
    }

    private List<Group> getGroupBy(
            final Collection<Student> students,
            final Function<Collection<Student>, List<Student>> func
    ) {
        return students.stream()
                .collect(Collectors.groupingBy(Student::getGroup))
                .entrySet().stream()
                .map(a -> new Group(a.getKey(), func.apply(a.getValue())))
                .sorted(Comparator.comparing(a -> a.getName().toString())).toList();
    }

    public GroupName getLargestGroup(final Collection<Student> students) {
        return getLargestIfEqual(
                students.stream()
                        .collect(Collectors.groupingBy(Student::getGroup))
                        .entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, a -> a.getValue().size()))
                        .entrySet(),
                Map.Entry.comparingByKey()
        );
    }

    public GroupName getLargestGroupFirstName(final Collection<Student> students) {
        return getLargestIfEqual(
                students.stream()
                        .collect(Collectors.groupingBy(Student::getGroup))
                        .entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, a -> getDistinctFirstNames(a.getValue()).size()))
                        .entrySet(),
                Map.Entry.comparingByKey(Comparator.reverseOrder())
        );
    }

    private GroupName getLargestIfEqual(
            final Set<Map.Entry<GroupName, Integer>> students,
            final Comparator<Map.Entry<GroupName, Integer>> comp
    ) {
        return students.stream()
                .max(Map.Entry.<GroupName, Integer>comparingByValue()
                        .thenComparing(comp)
                )
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public List<String> getFirstNames(final List<Student> students) {
        return getParam(students, Student::getFirstName);
    }

    public List<String> getLastNames(final List<Student> students) {
        return getParam(students, Student::getLastName);
    }

    public List<GroupName> getGroups(final List<Student> students) {
        return getParam(students, Student::getGroup);
    }

    public List<String> getFullNames(final List<Student> students) {
        return getParam(students, this::getName);
    }

    public Set<String> getDistinctFirstNames(final List<Student> students) {
        return new TreeSet<>(getParam(students, Student::getFirstName));
    }

    private <T> List<T> getParam(final List<Student> students, final Function<? super Student, ? extends T> param) {
        return students.stream().map(param).collect(Collectors.toList());
    }

    public String getMaxStudentFirstName(final List<Student> students) {
        return students.stream().max(Comparator.comparing(Student::getId)).map(Student::getFirstName).orElse("");
    }

    public List<Student> sortStudentsById(final Collection<Student> students) {
        return sortByComp(students, Student::compareTo);
    }

    public List<Student> sortStudentsByName(final Collection<Student> students) {
        return sortByComp(students, nameComparator);
    }

    private List<Student> sortByComp(final Collection<Student> students, final Comparator<Student> comp) {
        return students.stream().sorted(comp).toList();
    }

    public List<Student> findStudentsByFirstName(final Collection<Student> students, final String name) {
        return findByFunc(students, Student::getFirstName, name);
    }

    public List<Student> findStudentsByLastName(final Collection<Student> students, final String name) {
        return findByFunc(students, Student::getLastName, name);
    }

    public List<Student> findStudentsByGroup(final Collection<Student> students, final GroupName group) {
        return findByFunc(students, Student::getGroup, group);
    }

    private <T> List<Student> findByFunc(
            final Collection<Student> students,
            final Function<Student, T> func,
            final T toEqual
    ) {
        return students.stream()
                .filter(a -> func.apply(a).equals(toEqual))
                .sorted(nameComparator)
                .toList();
    }

    public Map<String, String> findStudentNamesByGroup(final Collection<Student> students, final GroupName group) {
        return students.stream()
                .filter(a -> a.getGroup().equals(group))
                .collect(Collectors.toMap(
                        Student::getLastName,
                        Student::getFirstName,
                        BinaryOperator.minBy(Comparable::compareTo))
                );
    }

    private String getName(final Student a) {
        return a.getFirstName() + " " + a.getLastName();
    }
}