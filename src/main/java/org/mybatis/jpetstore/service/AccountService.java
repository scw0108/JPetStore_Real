/*
 *    Copyright 2010-2023 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.jpetstore.service;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Optional;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.jpetstore.domain.Account;
import org.mybatis.jpetstore.mapper.AccountMapper;

/**
 * The Class AccountService.
 *
 * @author Eduardo Macarron
 */
public class AccountService {

  private AccountMapper accountMapper;
  private SqlSessionFactory sqlSessionFactory;

  public AccountService() throws IOException {
    String resource = "mybatis-config.xml";
    try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
      this.sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
    }
    this.accountMapper = sqlSessionFactory.openSession().getMapper(AccountMapper.class);
  }

  public Account getAccount(String username) {
    return accountMapper.getAccountByUsername(username);
  }

  public Account getAccount(String username, String password) {
    return accountMapper.getAccountByUsernameAndPassword(username, password);
  }

  public void insertAccount(Account account) throws SQLException {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      AccountMapper accountMapper = sqlSession.getMapper(AccountMapper.class);

      sqlSession.getConnection().setAutoCommit(false);

      try {
        accountMapper.insertAccount(account);
        accountMapper.insertProfile(account);
        accountMapper.insertSignon(account);

        sqlSession.commit();
      } catch (Exception e) {
        sqlSession.rollback();
        throw e;
      }
    }
  }

  public void updateAccount(Account account) throws SQLException {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      AccountMapper accountMapper = sqlSession.getMapper(AccountMapper.class);
      sqlSession.getConnection().setAutoCommit(false);

      try {
        accountMapper.updateAccount(account);
        accountMapper.updateProfile(account);

        Optional.ofNullable(account.getPassword()).filter(password -> password.length() > 0)
            .ifPresent(password -> accountMapper.updateSignon(account));

        sqlSession.commit();
      } catch (Exception e) {
        sqlSession.rollback();
        throw e;
      }
    }
  }
}
