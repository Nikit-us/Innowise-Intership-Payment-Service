package com.innowise.repository.impl;

import com.innowise.model.Payment;
import com.innowise.model.PaymentStatus;
import com.innowise.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
@RequiredArgsConstructor
@Slf4j
public class PaymentRepositoryImpl implements PaymentRepository {
    private final MongoTemplate mongoTemplate;

    private static final String ORDER_ID_KEY = "order_id";
    private static final String USER_ID_KEY = "user_id";
    private static final String STATUS_KEY = "status";
    private static final String TIMESTAMP_KEY = "timestamp";
    private static final String PAYMENT_AMOUNT_KEY = "payment_amount";
    private static final String TOTAL_SUM_KEY = "total_sum";


    @Override
    public Payment save(Payment payment) {
        return mongoTemplate.insert(payment);
    }

    @Override
    public List<Payment> findByOrderId(Long orderId) {
        return findByKey(ORDER_ID_KEY, orderId);
    }

    @Override
    public List<Payment> findByUserId(Long userId) {
        return findByKey(USER_ID_KEY, userId);
    }

    @Override
    public List<Payment> findByStatuses(Set<PaymentStatus> statuses) {
        log.info("Finding payments by statuses: {}", statuses);
        return findByKey(STATUS_KEY, statuses);
    }

    @Override
    public double getTotalSumOfDatePeriod(LocalDateTime startDate, LocalDateTime endDate) {
        MatchOperation matchOperation = match(
                where(TIMESTAMP_KEY)
                        .gte(startDate)
                        .lte(endDate)
        );

        GroupOperation groupOperation = group()
                .sum(PAYMENT_AMOUNT_KEY)
                .as(TOTAL_SUM_KEY);

        Aggregation aggregation = newAggregation(
                matchOperation,
                groupOperation
        );

        AggregationResults<Document> aggregationResults = mongoTemplate.aggregate(
                aggregation,
                Payment.class,
                Document.class
        );

        Document result = aggregationResults.getUniqueMappedResult();

        if (result != null && result.containsKey(TOTAL_SUM_KEY)) {
            Object totalSum = result.get(TOTAL_SUM_KEY);
            if (totalSum instanceof Number sum) {
                return sum.doubleValue();
            }
        }
        return 0.0;
    }

    private <T> List<Payment> findByKey(String key, T value) {
        Criteria criteria = where(key)
                .is(value.toString());
        Query query = new Query(criteria);
        return mongoTemplate.find(query, Payment.class);
    }

    private <T> List<Payment> findByKey(String key, Set<T> args) {
        Criteria criteria = where(key)
                .in(args);
        Query query = new Query(criteria);
        return mongoTemplate.find(query, Payment.class);
    }
}
