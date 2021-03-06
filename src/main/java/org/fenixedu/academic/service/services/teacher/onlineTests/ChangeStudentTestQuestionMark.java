/**
 * Copyright © 2002 Instituto Superior Técnico
 *
 * This file is part of FenixEdu Core.
 *
 * FenixEdu Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu Core.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fenixedu.academic.service.services.teacher.onlineTests;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.fenixedu.academic.domain.Attends;
import org.fenixedu.academic.domain.ExecutionCourse;
import org.fenixedu.academic.domain.Mark;
import org.fenixedu.academic.domain.onlineTests.DistributedTest;
import org.fenixedu.academic.domain.onlineTests.OnlineTest;
import org.fenixedu.academic.domain.onlineTests.Question;
import org.fenixedu.academic.domain.onlineTests.StudentTestLog;
import org.fenixedu.academic.domain.onlineTests.StudentTestQuestion;
import org.fenixedu.academic.domain.onlineTests.utils.ParseSubQuestion;
import org.fenixedu.academic.domain.student.Registration;
import org.fenixedu.academic.dto.InfoStudent;
import org.fenixedu.academic.service.filter.ExecutionCourseLecturingTeacherAuthorizationFilter;
import org.fenixedu.academic.service.services.exceptions.FenixServiceException;
import org.fenixedu.academic.service.services.exceptions.NotAuthorizedException;
import org.fenixedu.academic.util.Bundle;
import org.fenixedu.academic.util.tests.QuestionType;
import org.fenixedu.academic.util.tests.Response;
import org.fenixedu.academic.util.tests.ResponseLID;
import org.fenixedu.academic.util.tests.ResponseNUM;
import org.fenixedu.academic.util.tests.ResponseSTR;
import org.fenixedu.academic.util.tests.TestQuestionStudentsChangesType;
import org.fenixedu.academic.util.tests.TestType;
import org.fenixedu.bennu.core.i18n.BundleUtil;

import pt.ist.fenixframework.Atomic;
import pt.ist.fenixframework.FenixFramework;

public class ChangeStudentTestQuestionMark {
    protected void run(String executionCourseId, String distributedTestId, Double newMark, String questionId, String studentId,
            TestQuestionStudentsChangesType studentsType) throws FenixServiceException {

        DistributedTest distributedTest = FenixFramework.getDomainObject(distributedTestId);
        Question question = distributedTest.findQuestionByOID(questionId);

        List<StudentTestQuestion> studentsTestQuestionList = new ArrayList<StudentTestQuestion>();
        if (studentsType.getType().intValue() == TestQuestionStudentsChangesType.THIS_STUDENT) {
            final Registration registration = FenixFramework.getDomainObject(studentId);
            studentsTestQuestionList.add(StudentTestQuestion.findStudentTestQuestion(question, registration, distributedTest));
        } else if (studentsType.getType().intValue() == TestQuestionStudentsChangesType.STUDENTS_FROM_TEST_VARIATION) {
            studentsTestQuestionList.addAll(StudentTestQuestion.findStudentTestQuestions(question, distributedTest));
        } else if (studentsType.getType().intValue() == TestQuestionStudentsChangesType.STUDENTS_FROM_TEST) {
            final Registration registration = FenixFramework.getDomainObject(studentId);
            final StudentTestQuestion studentTestQuestion =
                    StudentTestQuestion.findStudentTestQuestion(question, registration, distributedTest);
            studentsTestQuestionList.addAll(distributedTest.findStudentTestQuestionsByTestQuestionOrder(studentTestQuestion
                    .getTestQuestionOrder()));
        } else if (studentsType.getType().intValue() == TestQuestionStudentsChangesType.ALL_STUDENTS) {
            studentsTestQuestionList.addAll(question.getStudentTestsQuestionsSet());
        }
        for (StudentTestQuestion studentTestQuestion : studentsTestQuestionList) {
            List<InfoStudent> group = new ArrayList<InfoStudent>();

            studentTestQuestion.setTestQuestionMark(newMark);
            if (!group.contains(studentTestQuestion.getStudent().getPerson())) {
                group.add(InfoStudent.newInfoFromDomain(studentTestQuestion.getStudent()));
            }

            if (studentTestQuestion.getDistributedTest().getTestType().equals(new TestType(TestType.EVALUATION))) {
                if (studentTestQuestion.getResponse() == null) {
                    Response response = null;
                    try {
                        ParseSubQuestion parse = new ParseSubQuestion();
                        studentTestQuestion = parse.parseStudentTestQuestion(studentTestQuestion);
                    } catch (Exception e) {
                        throw new FenixServiceException(e);
                    }

                    if (studentTestQuestion.getSubQuestionByItem().getQuestionType().getType().intValue() == QuestionType.STR) {
                        response = new ResponseSTR("");
                    } else if (studentTestQuestion.getSubQuestionByItem().getQuestionType().getType().intValue() == QuestionType.NUM) {
                        response = new ResponseNUM("");
                    } else if (studentTestQuestion.getSubQuestionByItem().getQuestionType().getType().intValue() == QuestionType.LID) {
                        response = new ResponseLID(new String[] { null });
                    }
                    response.setResponsed();
                    studentTestQuestion.setResponse(response);
                }
                OnlineTest onlineTest = studentTestQuestion.getDistributedTest().getOnlineTest();
                ExecutionCourse executionCourse = FenixFramework.getDomainObject(executionCourseId);
                Attends attend = studentTestQuestion.getStudent().readAttendByExecutionCourse(executionCourse);
                Mark mark = onlineTest.getMarkByAttend(attend);
                final String markValue =
                        getNewStudentMark(studentTestQuestion.getDistributedTest(), studentTestQuestion.getStudent());
                if (mark == null) {
                    mark = new Mark(attend, onlineTest, markValue);
                } else {
                    mark.setMark(markValue);
                }
                if (mark.getPublishedMark() != null) {
                    mark.setPublishedMark(markValue);
                }
            }
            String event = BundleUtil.getString(Bundle.APPLICATION, "message.changeStudentMarkLogMessage", newMark.toString());

            new StudentTestLog(studentTestQuestion.getDistributedTest(), studentTestQuestion.getStudent(), event);
        }
    }

    private String getNewStudentMark(DistributedTest dt, Registration s) {
        double totalMark = 0;
        Set<StudentTestQuestion> studentTestQuestionList = StudentTestQuestion.findStudentTestQuestions(s, dt);
        for (StudentTestQuestion studentTestQuestion : studentTestQuestionList) {
            totalMark += studentTestQuestion.getTestQuestionMark().doubleValue();
        }
        DecimalFormat df = new DecimalFormat("#0.##");
        DecimalFormatSymbols decimalFormatSymbols = df.getDecimalFormatSymbols();
        decimalFormatSymbols.setDecimalSeparator('.');
        df.setDecimalFormatSymbols(decimalFormatSymbols);
        return (df.format(Math.max(0, totalMark)));
    }

    // Service Invokers migrated from Berserk

    private static final ChangeStudentTestQuestionMark serviceInstance = new ChangeStudentTestQuestionMark();

    @Atomic
    public static void runChangeStudentTestQuestionMark(String executionCourseId, String distributedTestId, Double newMark,
            String questionId, String studentId, TestQuestionStudentsChangesType studentsType) throws FenixServiceException,
            NotAuthorizedException {
        ExecutionCourseLecturingTeacherAuthorizationFilter.instance.execute(executionCourseId);
        serviceInstance.run(executionCourseId, distributedTestId, newMark, questionId, studentId, studentsType);
    }

}