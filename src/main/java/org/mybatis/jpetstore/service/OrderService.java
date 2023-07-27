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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.jpetstore.domain.Item;
import org.mybatis.jpetstore.domain.LineItem;
import org.mybatis.jpetstore.domain.Order;
import org.mybatis.jpetstore.domain.Sequence;
import org.mybatis.jpetstore.mapper.ItemMapper;
import org.mybatis.jpetstore.mapper.LineItemMapper;
import org.mybatis.jpetstore.mapper.OrderMapper;
import org.mybatis.jpetstore.mapper.SequenceMapper;

/**
 * The Class OrderService.
 *
 * @author Eduardo Macarron
 */
public class OrderService {

  private final ItemMapper itemMapper;
  private final OrderMapper orderMapper;
  private final SequenceMapper sequenceMapper;
  private final LineItemMapper lineItemMapper;
  private SqlSessionFactory sqlSessionFactory;

  public OrderService(){
    String resource = "mybatis-config.xml";
    try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
      this.sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
    } catch (Exception e) {
      e.printStackTrace();
    }
    SqlSession session = sqlSessionFactory.openSession();
    this.orderMapper = session.getMapper(OrderMapper.class);
    this.itemMapper = session.getMapper(ItemMapper.class);
    this.sequenceMapper = session.getMapper(SequenceMapper.class);
    this.lineItemMapper = session.getMapper(LineItemMapper.class);
  }

  /**
   * Insert order.
   *
   * @param order
   *          the order
   */
  public void insertOrder(Order order) throws SQLException {
    order.setOrderId(getNextId("ordernum"));
    order.getLineItems().forEach(lineItem -> {
      String itemId = lineItem.getItemId();
      Integer increment = lineItem.getQuantity();
      Map<String, Object> param = new HashMap<>(2);
      param.put("itemId", itemId);
      param.put("increment", increment);
      itemMapper.updateInventoryQuantity(param);
    });

    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      OrderMapper orderMapper = sqlSession.getMapper(OrderMapper.class);
      LineItemMapper lineItemMapper = sqlSession.getMapper(LineItemMapper.class);
      sqlSession.getConnection().setAutoCommit(false);
      try {
        orderMapper.insertOrder(order);
        orderMapper.insertOrderStatus(order);

        order.getLineItems().forEach(lineItem -> {
          lineItem.setOrderId(order.getOrderId());
          lineItemMapper.insertLineItem(lineItem);
        });

        sqlSession.commit();
      } catch (Exception e) {
        sqlSession.rollback();
        throw e;
      }
    }
  }

  /**
   * Gets the order.
   *
   * @param orderId
   *          the order id
   *
   * @return the order
   */
  public Order getOrder(int orderId) throws SQLException {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      try {
        OrderMapper orderMapper = sqlSession.getMapper(OrderMapper.class);
        LineItemMapper lineItemMapper = sqlSession.getMapper(LineItemMapper.class);
        ItemMapper itemMapper = sqlSession.getMapper(ItemMapper.class);

        sqlSession.getConnection().setAutoCommit(false);

        Order order = orderMapper.getOrder(orderId);
        List<LineItem> lineItems = lineItemMapper.getLineItemsByOrderId(orderId);
        order.setLineItems(lineItems);

        lineItems.forEach(lineItem -> {
          Item item = itemMapper.getItem(lineItem.getItemId());
          item.setQuantity(itemMapper.getInventoryQuantity(lineItem.getItemId()));
          lineItem.setItem(item);
        });

        sqlSession.commit();

        return order;
      } catch (SQLException e) {
        sqlSession.rollback();
        throw e;
      }
    }
  }

  /**
   * Gets the orders by username.
   *
   * @param username
   *          the username
   *
   * @return the orders by username
   */
  public List<Order> getOrdersByUsername(String username) {
    return orderMapper.getOrdersByUsername(username);
  }

  /**
   * Gets the next id.
   *
   * @param name
   *          the name
   *
   * @return the next id
   */
  public int getNextId(String name) throws SQLException {
    Sequence sequence = sequenceMapper.getSequence(new Sequence(name, -1));
    if (sequence == null) {
      throw new RuntimeException(
          "Error: A null sequence was returned from the database (could not get next " + name + " sequence).");
    }
    Sequence parameterObject = new Sequence(name, sequence.getNextId() + 1);
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      try {
        SequenceMapper sequenceMapper = sqlSession.getMapper(SequenceMapper.class);
        sqlSession.getConnection().setAutoCommit(false);
        sequenceMapper.updateSequence(parameterObject);
        sqlSession.commit();
        return sequence.getNextId();
      } catch (SQLException e) {
        sqlSession.rollback();
        throw e;
      }
    }
  }

}
