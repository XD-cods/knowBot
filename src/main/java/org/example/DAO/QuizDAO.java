package org.example.DAO;

import org.example.model.Quiz;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.Query;

import java.util.List;

public class QuizDAO {
  private static final SessionFactory sessionFactory = new Configuration()
          .configure("hibernate.config.xml")
          .buildSessionFactory();

  public List<Quiz> getAllQuiz() {
    try (Session session = sessionFactory.openSession()) {
      Query<Quiz> query = session.createQuery("from Quiz", Quiz.class);
      List<Quiz> quizzes = query.list();
      if (quizzes.isEmpty()) {
        return List.of();
      }
      return quizzes;
    }
  }

  public Quiz getByTopicName(String topicName) {
    try (Session session = sessionFactory.openSession()) {
      Query<Quiz> query = session.createQuery("from Quiz where topicName = :topicName", Quiz.class);
      query.setParameter("topicName", topicName);
      Quiz quiz = query.getSingleResult();
      System.out.println(quiz.toString());
      if (quiz == null) {
        return new Quiz();
      }
      return quiz;
    }
  }

  public String[] getAllTopicName() {
    try (Session session = sessionFactory.openSession()) {
      Query<String> query = session.createQuery("select topicName from Quiz", String.class);
      List<String> allTopicName = query.list();
      if (allTopicName.isEmpty()) {
        return List.of().toArray(new String[]{});
      }
      return allTopicName.toArray(new String[]{});
    }
  }

  public void addNewQuiz(Quiz quiz) {
    try (Session session = sessionFactory.openSession()) {
      session.beginTransaction();
      session.persist(quiz);
      session.getTransaction().commit();
    }
  }

  public int getCountOfQuiz(){
    try (Session session = sessionFactory.openSession()) {
      Query<String> query = session.createQuery("select topicName from Quiz", String.class);
      List<String> topicList = query.list();
      return topicList.size();
    }
  }


}
