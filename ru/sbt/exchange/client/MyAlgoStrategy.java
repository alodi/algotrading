package ru.sbt.exchange.client;

import ru.sbt.exchange.domain.Direction;
import ru.sbt.exchange.domain.ExchangeEvent;
import ru.sbt.exchange.domain.ExchangeEventType;
import ru.sbt.exchange.domain.Order;
import ru.sbt.exchange.domain.instrument.Bond;
import ru.sbt.exchange.domain.instrument.Instrument;
import ru.sbt.exchange.domain.instrument.Instruments;

import java.util.Random;

import static ru.sbt.exchange.domain.Direction.BUY;
import static ru.sbt.exchange.domain.Direction.SELL;
import static ru.sbt.exchange.domain.ExchangeEventType.NEW_PERIOD_START;
import static ru.sbt.exchange.domain.ExchangeEventType.ORDER_NEW;

public class MyAlgoStrategy implements AlgoStrategy {
    private Double payoffPercent = 0.;
    private Double partOfBonds = 1.;

    @Override
    public void onEvent(ExchangeEvent event, Broker broker) {

        if (event.getExchangeEventType() == NEW_PERIOD_START) {
            broker.cancelOrdersByInstrument(Instruments.zeroCouponBond());
            broker.cancelOrdersByInstrument(Instruments.fixedCouponBond());
            broker.cancelOrdersByInstrument(Instruments.floatingCouponBond());
        }

        if (event.getExchangeEventType() == ORDER_NEW) {
            Order order = event.getOrder();
            Instrument instrument = order.getInstrument();

            if (order.getDirection() == SELL && order.getPrice() < priceOfBond(broker, (Bond) instrument)) {
                double money = broker.getMyPortfolio().getMoney() + broker.getMyPortfolio().getAcceptedOverdraft();
                int quantity = (int) Math.min(money / order.getPrice(), order.getQuantity());
                quantity = (int) (quantity * partOfBonds);
                Order buyOrder = order.opposite().withQuantity(quantity);
                broker.addOrder(buyOrder);
            }

            if (order.getDirection() == BUY && order.getPrice() > priceOfBond(broker, (Bond) instrument)) {
                int quantityOfInstrument = broker.getMyPortfolio().getCountByInstrument().get(instrument);
                int orderQuantity = order.getQuantity();
                int quantity = Math.min(quantityOfInstrument, orderQuantity);
                quantity = (int) (quantity * partOfBonds);
                Order sellOrder = order.opposite().withQuantity(quantity);
                broker.addOrder(sellOrder);
                if (quantityOfInstrument < orderQuantity && order.getPrice() > priceOfShortBond(broker, (Bond) instrument)) {
                    quantity = orderQuantity - quantityOfInstrument;
                    quantity = (int) ((quantity) * partOfBonds);
                    Order shortSellOrder = order.opposite().withQuantity(quantity);
                    broker.addOrder(shortSellOrder);
                }
            }
        }
    }

    private double priceOfBond(Broker broker, Bond bond) {
        double nominal = bond.getNominal();
        double averageCoup = (bond.getCouponInPercents().getMax() + bond.getCouponInPercents().getMin()) * 0.5;
        double rate = broker.getMyPortfolio().getPeriodInterestRate();
        double brokerPercent = broker.getMyPortfolio().getBrokerFeeInPercents();
        int periods = Math.max(0, broker.getPeriodInfo().getEndPeriodNumber() - broker.getPeriodInfo().getCurrentPeriodNumber());
        double price = nominal * Math.pow((100. + averageCoup) / (100. + rate), periods) * (100. + brokerPercent) / 100.;
        return price;
    }

    private double priceOfShortBond(Broker broker, Bond bond) {
        double nominal = bond.getNominal();
        double averageCoup = (bond.getCouponInPercents().getMax() + bond.getCouponInPercents().getMin()) * 0.5;
        double rate = broker.getMyPortfolio().getPeriodInterestRate();
        double percent = broker.getMyPortfolio().getBrokerFeeInPercents();
        int periods = 0;
        if (broker.getPeriodInfo().getCurrentPeriodNumber() == broker.getPeriodInfo().getEndPeriodNumber()) periods = 1;
        double price = nominal * Math.pow((100. + averageCoup) / (100. + rate) * (100. + percent), periods) / 100.;
        return price;
    }

}