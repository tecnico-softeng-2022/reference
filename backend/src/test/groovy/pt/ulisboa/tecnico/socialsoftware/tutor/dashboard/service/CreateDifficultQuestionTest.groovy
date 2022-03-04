package pt.ulisboa.tecnico.socialsoftware.tutor.dashboard.service

import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import pt.ulisboa.tecnico.socialsoftware.tutor.BeanConfiguration
import pt.ulisboa.tecnico.socialsoftware.tutor.SpockTest
import pt.ulisboa.tecnico.socialsoftware.tutor.dashboard.domain.Dashboard
import pt.ulisboa.tecnico.socialsoftware.tutor.question.domain.Course
import pt.ulisboa.tecnico.socialsoftware.tutor.question.domain.MultipleChoiceQuestion
import pt.ulisboa.tecnico.socialsoftware.tutor.question.domain.Option
import pt.ulisboa.tecnico.socialsoftware.tutor.question.domain.Question
import pt.ulisboa.tecnico.socialsoftware.tutor.user.domain.Student
import pt.ulisboa.tecnico.socialsoftware.tutor.exceptions.ErrorMessage
import pt.ulisboa.tecnico.socialsoftware.tutor.exceptions.TutorException
import spock.lang.Unroll
import pt.ulisboa.tecnico.socialsoftware.tutor.utils.DateHandler


import static pt.ulisboa.tecnico.socialsoftware.tutor.exceptions.ErrorMessage.CANNOT_CREATE_DIFFICULT_QUESTION
import static pt.ulisboa.tecnico.socialsoftware.tutor.exceptions.ErrorMessage.DASHBOARD_NOT_FOUND
import static pt.ulisboa.tecnico.socialsoftware.tutor.exceptions.ErrorMessage.QUESTION_NOT_FOUND

@DataJpaTest
class CreateDifficultQuestionTest extends SpockTest {
    def student
    def dashboard
    def question

    def setup() {
        createExternalCourseAndExecution()

        student = new Student(USER_1_NAME, false)
        student.addCourse(externalCourseExecution)
        userRepository.save(student)

        question = new Question()
        question.setTitle(QUESTION_1_TITLE)
        question.setContent(QUESTION_1_CONTENT)
        question.setStatus(Question.Status.AVAILABLE)
        question.setNumberOfAnswers(2)
        question.setNumberOfCorrect(1)
        question.setCourse(externalCourse)
        def questionDetails = new MultipleChoiceQuestion()
        question.setQuestionDetails(questionDetails)
        questionDetailsRepository.save(questionDetails)
        questionRepository.save(question)

        dashboard = new Dashboard(externalCourseExecution, student)
        dashboardRepository.save(dashboard)
    }

    @Unroll
    def "create difficult question with percentage #percentage"() {
        when: "a weekly score is created"
        difficultQuestionService.createDifficultQuestions(dashboard.getId(), question.getId(), percentage)

        then: "the weekly score is inside the weekly score repository and with the correct data"
        difficultQuestionRepository.count() == 1L
        def result = difficultQuestionRepository.findAll().get(0)
        result.getId() != null
        result.getDashboard().getId() == dashboard.getId()
        result.getQuestion().getId() == question.getId()
        result.isRemoved() == false
        result.getRemovedDate() == null
        result.getPercentage() == percentage
        and: "dashboard contains the difficult question"
        def dashboard = dashboardRepository.getById(dashboard.getId())
        dashboard.getDifficultQuestions().contains(result)
        dashboard.getLastCheckDifficultQuestions().isAfter(DateHandler.now().minusMinutes(1))

        where:
        percentage << [0, 12, 24]
    }

    def "cannot create two difficult questions for the same question"() {
        given: "a difficult question"
        difficultQuestionService.createDifficultQuestions(dashboard.getId(), question.getId(), 13)

        when: "when it is created a new difficult question for the same question"
        difficultQuestionService.createDifficultQuestions(dashboard.getId(), question.getId(), 24)

        then:
        def exception = thrown(TutorException)
        exception.getErrorMessage() == ErrorMessage.DIFFICULT_QUESTION_ALREADY_CREATED
        and: "there is a difficult question in the database"
        difficultQuestionRepository.count() == 1L
    }

    def "cannot create a difficult question that does not belong to the course"() {
        given: "another course"
        def alienCourse = new Course(COURSE_1_NAME, Course.Type.TECNICO)
        courseRepository.save(alienCourse)
        and: "a difficult question"
        def alienQuestion = new Question()
        alienQuestion.setTitle(QUESTION_1_TITLE)
        alienQuestion.setContent(QUESTION_1_CONTENT)
        alienQuestion.setStatus(Question.Status.AVAILABLE)
        alienQuestion.setNumberOfAnswers(2)
        alienQuestion.setNumberOfCorrect(1)
        alienQuestion.setCourse(alienCourse)
        def questionDetails = new MultipleChoiceQuestion()
        alienQuestion.setQuestionDetails(questionDetails)
        questionDetailsRepository.save(questionDetails)
        questionRepository.save(alienQuestion)

        when: "when it is created a new difficult question for the same question"
        difficultQuestionService.createDifficultQuestions(dashboard.getId(), alienQuestion.getId(), 22)

        then:
        def exception = thrown(TutorException)
        exception.getErrorMessage() == ErrorMessage.CANNOT_CREATE_DIFFICULT_QUESTION
        and: "there is a difficult question in the database"
        difficultQuestionRepository.count() == 0L
    }

    @Unroll
    def "cannot create difficult question with percentage=#percentage"() {
        when: "a weekly score is created"
        difficultQuestionService.createDifficultQuestions(dashboard.getId(), question.getId(), percentage)

        then: "an exception is thrown"
        def exception = thrown(TutorException)
        exception.getErrorMessage() == CANNOT_CREATE_DIFFICULT_QUESTION
        weeklyScoreRepository.count() == 0L

        where:
        percentage << [-100, -1, 25, 50, 150]

    }

    @Unroll
    def "cannot create difficult question with dashboardId=#dashboardId"() {
        when: "a weekly score is created"
        difficultQuestionService.createDifficultQuestions(dashboardId, question.getId(), 20)

        then: "an exception is thrown"
        def exception = thrown(TutorException)
        exception.getErrorMessage() == DASHBOARD_NOT_FOUND
        weeklyScoreRepository.count() == 0L

        where:
        dashboardId << [0, 100]
    }

    @Unroll
    def "cannot create difficult question with questionId=#questionId"() {
        when: "a weekly score is created"
        difficultQuestionService.createDifficultQuestions(dashboard.getId(), questionId, 20)

        then: "an exception is thrown"
        def exception = thrown(TutorException)
        exception.getErrorMessage() == QUESTION_NOT_FOUND
        weeklyScoreRepository.count() == 0L

        where:
        questionId << [0, 100]
    }


    @TestConfiguration
    static class LocalBeanConfiguration extends BeanConfiguration {}
}