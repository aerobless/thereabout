package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.finance.data.ReferenceRate;
import java.io.ByteArrayInputStream;
import java.math.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EcbRateClient {
  private final Clock financeClock;

  public record Download(List<ReferenceRate> values, LocalDate latest) {}

  public Download download() {
    try {
      var response =
          HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(15))
              .build()
              .send(
                  HttpRequest.newBuilder(
                          URI.create(
                              "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.xml"))
                      .timeout(Duration.ofSeconds(45))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.ofByteArray());
      require(response.statusCode() == 200, "ECB download failed");
      require(response.body().length < 20_000_000, "ECB response too large");
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(response.body()));
      var cubes = doc.getElementsByTagName("Cube");
      List<ReferenceRate> inserts = new ArrayList<>();
      LocalDate latest = LocalDate.MIN;
      for (int i = 0; i < cubes.getLength(); i++) {
        var element = (org.w3c.dom.Element) cubes.item(i);
        if (!element.hasAttribute("time")) continue;
        LocalDate date = LocalDate.parse(element.getAttribute("time"));
        if (date.isAfter(LocalDate.now(financeClock))) continue;
        if (date.isAfter(latest)) latest = date;
        Map<String, BigDecimal> quotes = new HashMap<>();
        quotes.put("EUR", BigDecimal.ONE);
        var children = element.getChildNodes();
        for (int j = 0; j < children.getLength(); j++)
          if (children.item(j) instanceof org.w3c.dom.Element child
              && child.hasAttribute("currency"))
            quotes.put(child.getAttribute("currency"), new BigDecimal(child.getAttribute("rate")));
        BigDecimal chf = quotes.get("CHF");
        if (chf == null) continue;
        for (var pair : quotes.entrySet())
          if (!pair.getKey().equals("CHF"))
            inserts.add(
                new ReferenceRate(
                    pair.getKey(),
                    "CHF",
                    date,
                    chf.divide(pair.getValue(), 24, RoundingMode.HALF_EVEN)));
      }
      require(!inserts.isEmpty(), "No ECB rates found");
      return new Download(List.copyOf(inserts), latest);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("ECB refresh interrupted; existing rates retained", e);
    } catch (Exception e) {
      throw new IllegalStateException("ECB refresh failed; existing rates retained", e);
    }
  }
}
