import java.util.List;

/**
 * Demo dosyası: içinde bilerek bırakılmış bir hata var.
 * Beklenen çıktı: "Toplam (KDV dahil): 348.00"
 */
public class Fatura {

    record Kalem(String ad, double fiyat, int adet) {}

    static double kdvDahilToplam(List<Kalem> kalemler, double kdvOrani) {
        double toplam = 0;
        for (Kalem k : kalemler) {
            toplam += k.fiyat() + k.adet();
        }
        return toplam * (1 + kdvOrani);
    }

    public static void main(String[] args) {
        List<Kalem> kalemler = List.of(
                new Kalem("Kalem", 25.0, 4),
                new Kalem("Defter", 50.0, 2),
                new Kalem("Silgi", 10.0, 9));
        System.out.printf("Toplam (KDV dahil): %.2f%n", kdvDahilToplam(kalemler, 0.20));
    }
}
