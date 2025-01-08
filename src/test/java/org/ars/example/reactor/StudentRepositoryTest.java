package org.ars.example.reactor;

import org.ars.example.reactor.entity.Student;
import org.ars.example.reactor.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class StudentRepositoryTest {

    @Autowired
    StudentRepository studentRepository;

    @BeforeEach
    void init() {
        studentRepository.saveAll(List.of(
                Student.builder().id(1L).name("John").age(23).build(),
                Student.builder().id(2L).name("David").age(29).build(),
                Student.builder().id(3L).name("Simon").age(35).build()
        ));
    }
    @Test
    void findAll() {
        assertThat(studentRepository.findAll()).hasSize(3);
    }
}
