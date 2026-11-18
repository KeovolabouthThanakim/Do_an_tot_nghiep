package com.kosis.shop.service;

import com.kosis.shop.entity.Product;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductCsvParserTest {

    private static final String HEADER = "code,name,category,gender,list_price,discount_percent,color,color_hex,size,qty,cost";

    private static ProductCsvParser.Result parse(String... lines) {
        return ProductCsvParser.parse(String.join("\n", lines), code -> false);
    }

    private static boolean hasError(ProductCsvParser.Result r, String fragment) {
        return r.errors().stream().anyMatch(e -> e.contains(fragment));
    }

    @Test
    void sampleFileIsValid() {
        ProductCsvParser.Result r = ProductCsvParser.parse(ProductCsvParser.SAMPLE_CSV, code -> false);
        assertTrue(r.ok(), r.errors().toString());
        assertEquals(3, r.products().size());

        StockImportService.NewProduct shirt = r.products().get(0);
        assertEquals("KS101", shirt.product().getCode());
        assertEquals(3, shirt.opening().size());           // dòng qty = 0 không tạo dòng nhập

        StockImportService.NewProduct bag = r.products().get(1);
        assertEquals(621_000, bag.product().getPrice());   // 690.000 giảm 10%
        assertEquals(690_000, bag.product().getOldPrice());
        assertEquals(1, bag.opening().size());

        assertTrue(r.products().get(2).opening().isEmpty());   // không có số lượng -> chờ nhập hàng
    }

    @Test
    void rowsWithSameCodeBecomeOneProductWithColorsAndSizes() {
        ProductCsvParser.Result r = parse(HEADER,
                "A1,Áo thử,Áo,MEN,450000,0,Olive,#6b7a46,M,10,200000",
                "A1,,,,,,Olive,#6b7a46,L,5,200000",
                "A1,,,,,,Đen,111111,M,8,210000");
        assertTrue(r.ok(), r.errors().toString());
        assertEquals(1, r.products().size());
        Product p = r.products().get(0).product();
        assertEquals("Olive:#6b7a46,Đen:#111111", p.getColors());
        assertEquals("M,L", p.getSizes());
        assertEquals(3, r.products().get(0).opening().size());
        assertEquals(new StockImportService.OpeningLine("M", "Đen", 8, 210_000), r.products().get(0).opening().get(2));
    }

    @Test
    void noSizeColumnValuesMeansNoSizes() {
        ProductCsvParser.Result r = parse(HEADER, "B1,Túi,Túi,UNISEX,100000,0,,,,3,50000");
        assertTrue(r.ok(), r.errors().toString());
        assertEquals("-", r.products().get(0).product().getSizes());
        assertNull(r.products().get(0).opening().get(0).size());
        assertNull(r.products().get(0).opening().get(0).color());
    }

    @Test
    void acceptsSemicolonsQuotesAndThousandSeparators() {
        ProductCsvParser.Result r = parse(
                "code;name;category;gender;list_price;size;qty;cost",
                "C1;\"Áo \"\"đặc biệt\"\"; dài\";Áo;MEN;1.250.000;m;3;100.000");
        assertTrue(r.ok(), r.errors().toString());
        Product p = r.products().get(0).product();
        assertEquals("Áo \"đặc biệt\"; dài", p.getName());
        assertEquals(1_250_000, p.getPrice());
        assertEquals("M", r.products().get(0).opening().get(0).size());
        assertEquals(100_000, r.products().get(0).opening().get(0).unitCost());
    }

    @Test
    void quantityWithoutCostIsRejectedWithLineNumber() {
        ProductCsvParser.Result r = parse(HEADER, "D1,Áo,Áo,MEN,100000,0,,,M,5,");
        assertFalse(r.ok());
        assertTrue(r.products().isEmpty());
        assertTrue(hasError(r, "Dòng 2"), r.errors().toString());
        assertTrue(hasError(r, "cost"), r.errors().toString());
    }

    @Test
    void anyErrorMeansNothingIsCreated() {
        ProductCsvParser.Result r = parse(HEADER,
                "E1,Áo tốt,Áo,MEN,100000,0,,,M,1,10",
                "E2,Sai loại,Không có loại,MEN,100000,0,,,M,1,10");
        assertFalse(r.ok());
        assertTrue(r.products().isEmpty());
        assertTrue(hasError(r, "Dòng 3"), r.errors().toString());
    }

    @Test
    void existingCodeIsRejected() {
        ProductCsvParser.Result r = ProductCsvParser.parse(
                HEADER + "\nF1,Áo,Áo,MEN,100000,0,,,M,1,10", code -> code.equals("F1"));
        assertFalse(r.ok());
        assertTrue(hasError(r, "đã tồn tại"), r.errors().toString());
    }

    @Test
    void conflictingCommonFieldsAndDuplicateVariantsAreRejected() {
        ProductCsvParser.Result r = parse(HEADER,
                "G1,Áo A,Áo,MEN,100000,0,Đỏ,#ff0000,M,1,10",
                "G1,Áo B,,,,,Đỏ,#ff0000,M,1,10");
        assertFalse(r.ok());
        assertTrue(hasError(r, "name"), r.errors().toString());
        assertTrue(hasError(r, "trùng"), r.errors().toString());
    }

    @Test
    void missingColumnsAndEmptyFilesAreRejected() {
        assertFalse(ProductCsvParser.parse("code,name\nA,B\n", c -> false).ok());
        assertTrue(hasError(ProductCsvParser.parse("code,name\nA,B\n", c -> false), "list_price"));
        assertFalse(ProductCsvParser.parse("", c -> false).ok());
        assertFalse(parse(HEADER).ok());
    }

    @Test
    void mixedColorAndPlainRowsAreRejected() {
        ProductCsvParser.Result r = parse(HEADER,
                "H1,Áo,Áo,MEN,100000,0,Đỏ,#ff0000,M,1,10",
                "H1,,,,,,,,L,1,10");
        assertFalse(r.ok());
        assertTrue(hasError(r, "thiếu màu"), r.errors().toString());
    }

    @Test
    void decodeRequiresUtf8AndStripsBom() {
        assertEquals("a", ProductCsvParser.decode(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a'}));
        assertEquals("Áo", ProductCsvParser.decode("Áo".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> ProductCsvParser.decode(new byte[]{(byte) 0xE1, 0x20}));
    }

    @Test
    void sampleFileMatchesRequiredHeader() {
        List<String> head = List.of(ProductCsvParser.SAMPLE_CSV.split("\r\n")[0].split(","));
        assertTrue(head.containsAll(List.of("code", "name", "category", "gender", "list_price", "qty", "cost")));
    }
}
