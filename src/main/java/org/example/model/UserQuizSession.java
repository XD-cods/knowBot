package org.example.model;

import java.util.Collection;
import java.util.Iterator;
public class UserQuizSession {

  private final int questionAmount;
  private final Iterator<Question> questionIterator;
  private Question currentQuestion;
  private int questionCounter = 0;
  private int rightAnswerCounter = 0;
  private boolean quizMode = true;

  public UserQuizSession(Collection<Question> questions) {
    this.questionIterator = questions.iterator();
    this.questionAmount = questions.size();
//    this.currentQuestion = this.questionIterator.next();
  }

  public boolean isQuizMode() {
    return quizMode;
  }

  public boolean isNextQuestionAvailable() {
    return questionIterator.hasNext();
  }

  public void addRightCounter() {
    rightAnswerCounter++;
  }


  public void setQuizMode(boolean quizMode) {
    this.quizMode = quizMode;
  }

  public Question getCurrentQuestion() {
    return currentQuestion;
  }

  public Question getNextQuestion() {
    questionCounter++;
    return currentQuestion = questionIterator.next();
  }

  public int getQuestionCounter() {
    return questionCounter;
  }

  public int getQuestionAmount() {
    return questionAmount;
  }

  public int getRightAnswerCounter() {
    return rightAnswerCounter;
  }
}
