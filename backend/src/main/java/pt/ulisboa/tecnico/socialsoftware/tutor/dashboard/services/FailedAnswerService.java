package pt.ulisboa.tecnico.socialsoftware.tutor.dashboard.services;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.socialsoftware.tutor.answer.domain.QuestionAnswer;
import pt.ulisboa.tecnico.socialsoftware.tutor.answer.domain.QuizAnswer;
import pt.ulisboa.tecnico.socialsoftware.tutor.answer.repository.QuestionAnswerRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.answer.repository.QuizAnswerRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.dashboard.domain.Dashboard;
import pt.ulisboa.tecnico.socialsoftware.tutor.dashboard.domain.FailedAnswer;
import pt.ulisboa.tecnico.socialsoftware.tutor.dashboard.dto.FailedAnswerDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.dashboard.dto.UpdatedFailedAnswersDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.dashboard.repository.DashboardRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.dashboard.repository.FailedAnswerRepository;

import pt.ulisboa.tecnico.socialsoftware.tutor.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.socialsoftware.tutor.exceptions.TutorException;
import pt.ulisboa.tecnico.socialsoftware.tutor.utils.DateHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static pt.ulisboa.tecnico.socialsoftware.tutor.exceptions.ErrorMessage.*;

@Service
public class FailedAnswerService {

    @Autowired
    private DashboardRepository dashboardRepository;

    @Autowired
    private FailedAnswerRepository failedAnswerRepository;

    @Autowired
    private QuestionAnswerRepository questionAnswerRepository;

    @Autowired
    private QuizAnswerRepository quizAnswerRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FailedAnswerDto createFailedAnswer(int dashboardId, int questionAnswerId) {
        Dashboard dashboard = dashboardRepository.findById(dashboardId).orElseThrow(() -> new TutorException(ErrorMessage.DASHBOARD_NOT_FOUND, dashboardId));
        QuestionAnswer questionAnswer = questionAnswerRepository.findById(questionAnswerId).orElseThrow(() -> new TutorException(QUESTION_ANSWER_NOT_FOUND, questionAnswerId));

        FailedAnswer failedAnswer = new FailedAnswer(dashboard, questionAnswer, DateHandler.now());
        failedAnswerRepository.save(failedAnswer);

        return new FailedAnswerDto(failedAnswer);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void removeFailedAnswer(int failedAnswerId) {
        FailedAnswer toRemove = failedAnswerRepository.findById(failedAnswerId).orElseThrow(() -> new TutorException(ErrorMessage.FAILED_ANSWER_NOT_FOUND, failedAnswerId));
        toRemove.remove();
        failedAnswerRepository.delete(toRemove);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<FailedAnswerDto> getFailedAnswers(int dashboardId) {
        Dashboard dashboard = dashboardRepository.findById(dashboardId).orElseThrow(() -> new TutorException(ErrorMessage.DASHBOARD_NOT_FOUND, dashboardId));

        Set<FailedAnswer> failedAnswers = dashboard.getFailedAnswers();

        return failedAnswers.stream()
                .map(FailedAnswerDto::new)
                .sorted(Comparator.comparing(FailedAnswerDto::getCollected, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UpdatedFailedAnswersDto updateFailedAnswers(int dashboardId, String startDate, String endDate) {
        Dashboard dashboard = dashboardRepository.findById(dashboardId).orElseThrow(() -> new TutorException(ErrorMessage.DASHBOARD_NOT_FOUND, dashboardId));

        LocalDateTime now = DateHandler.now();

        LocalDateTime start, end;
        if (startDate == null) start = getLastCheckDate(dashboard, now);
        else start = LocalDateTime.parse(startDate, DateTimeFormatter.ISO_DATE_TIME);
        if (endDate == null) end = now;
        else end = LocalDateTime.parse(endDate, DateTimeFormatter.ISO_DATE_TIME);

        dashboard.getStudent().getQuizAnswers().stream()
                .filter(quizAnswer -> quizAnswer.getQuiz().getCourseExecution() == dashboard.getCourseExecution())
                .filter(quizAnswer -> quizAnswer.canResultsBePublic(dashboard.getCourseExecution().getId()))
                .filter(quizAnswer -> quizAnswer.getAnswerDate().isAfter(start) && quizAnswer.getAnswerDate().isBefore(end))
                .flatMap(quizAnswer -> quizAnswer.getQuestionAnswers().stream())
                .filter(Predicate.not(QuestionAnswer::isCorrect))
                .filter(qa -> dashboard.getFailedAnswers().stream().noneMatch(fa -> Objects.equals(fa.getQuestionAnswer().getId(), qa.getId())))
                .forEach(questionAnswer -> createFailedAnswer(dashboardId, questionAnswer.getId()));

        if (startDate == null && endDate == null) {
            dashboard.setLastCheckFailedAnswers(dashboard.getStudent().getQuizAnswers().stream()
                    .filter(quizAnswer -> quizAnswer.getQuiz().getCourseExecution() == dashboard.getCourseExecution())
                    .filter(quizAnswer -> !quizAnswer.canResultsBePublic(dashboard.getCourseExecution().getId()))
                    .filter(quizAnswer -> dashboard.getLastCheckFailedAnswers() == null
                            || quizAnswer.getCreationDate().isAfter(dashboard.getLastCheckFailedAnswers()))
                    .map(QuizAnswer::getCreationDate)
                    .sorted()
                    .findFirst()
                    .map(localDateTime -> localDateTime.minusSeconds(1))
                    .orElse(now));
        }

        return new UpdatedFailedAnswersDto(dashboard);
    }

    private LocalDateTime getLastCheckDate(Dashboard dashboard, LocalDateTime now) {
        LocalDateTime startCheckDate;
        if (dashboard.getLastCheckFailedAnswers() == null) {
            startCheckDate = dashboard.getStudent().getQuizAnswers().stream()
                    .filter(quizAnswer -> quizAnswer.getQuiz().getCourseExecution() == dashboard.getCourseExecution())
                    .map(QuizAnswer::getCreationDate)
                    .sorted()
                    .findFirst()
                    .map(localDateTime -> localDateTime.minusSeconds(1))
                    .orElse(now);
        } else {
            startCheckDate = dashboard.getLastCheckFailedAnswers();
        }

        return startCheckDate;
    }
}