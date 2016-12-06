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
            double money = broker.getMyPortfolio().getMoney() + broker.getMyPortfolio().getAcceptedOverdraft();
            int quantityToBuy = (int) Math.min(money / order.getPrice(), order.getQuantity());
            Integer quantityMyBonds = broker.getMyPortfolio().getCountByInstrument().get(instrument);
            int quantityToSell = Math.min(quantityMyBonds, order.getQuantity());

            if (order.getDirection() == SELL && order.getPrice() < priceBond(broker, (Bond) instrument, BUY)) {
                Order BuyOrder = order.opposite().withQuantity((int) (quantityToBuy * partOfBonds));
                broker.addOrder(BuyOrder);
            }

            if (order.getDirection() == BUY && order.getPrice() > priceBond(broker, (Bond) instrument, SELL)) {
                if (order.getPrice() > priceBondInPeriod(broker, (Bond) instrument, SELL)) {
                    quantityToSell = order.getQuantity();
                }
                Order BuyOrder = order.opposite().withQuantity((int) (quantityToSell * partOfBonds));
                broker.addOrder(BuyOrder);
            }
        }
    }

    private double priceBond(Broker broker, Bond bond, Direction direction) {
        double nominal = bond.getNominal();
        double averageCoup = (bond.getCouponInPercents().getMax() + bond.getCouponInPercents().getMin()) * 0.5;
        double rate = broker.getMyPortfolio().getPeriodInterestRate();
        double percent = broker.getMyPortfolio().getBrokerFeeInPercents();
        int periods = Math.max(1, bond.getMaturityPeriod() - broker.getPeriodInfo().getCurrentPeriodNumber());
        double price = nominal * Math.pow((100. + averageCoup) / (100. + rate), periods) * (100. + percent) / 100.;
        double priceWithPayoff;

        if (direction == SELL) {
            priceWithPayoff = price * Math.pow((100. + payoffPercent) / 100., periods);
        } else priceWithPayoff = price * Math.pow((100. - payoffPercent) / 100., periods);
        return priceWithPayoff;
    }

    private double priceBondInPeriod(Broker broker, Bond bond, Direction direction) {
        double nominal = bond.getNominal();
        double averageCoup = (bond.getCouponInPercents().getMax() + bond.getCouponInPercents().getMin()) * 0.5;
        double rate = broker.getMyPortfolio().getPeriodInterestRate();
        double percent = broker.getMyPortfolio().getBrokerFeeInPercents();
        double price = nominal * (100. + averageCoup) / (100. + rate) * (100. + percent) / 100.;
        double priceWithPayoff;

        if (direction == SELL) {
            priceWithPayoff = price * (100. + payoffPercent) / 100.;
        } else priceWithPayoff = price * (100. - payoffPercent) / 100.;
        return priceWithPayoff;
    }

}