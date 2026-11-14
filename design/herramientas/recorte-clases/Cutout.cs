using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class Cutout
{
    // Parameters (tunable from PowerShell)
    public static int BgTol = 40;          // color distance to treat as parchment
    public static double ShadowSpread = 0.09; // ratio spread for "darker parchment" (shadows, vignette)
    public static double ShadowMinK = 0.55;   // darkest shadow factor treated as background
    public static int HoleTol = 26;        // tighter tolerance for enclosed holes
    public static int HoleMinArea = 2500;  // enclosed parchment pockets bigger than this are removed
    public static double FragMaxRatio = 0.02; // detached fragments kept if inside main bbox and smaller than this
    public static int Pad = 12;
    public static int NearR = 6;

    static int W, H;
    static byte[] px; // BGRA
    static int bgR, bgG, bgB;

    static int Dist(int i)
    {
        int b = px[i * 4], g = px[i * 4 + 1], r = px[i * 4 + 2];
        int dr = r - bgR, dg = g - bgG, db = b - bgB;
        return (int)Math.Sqrt(dr * dr + dg * dg + db * db);
    }

    public static int LightMin = 228;      // near-white inset panels
    public static int LightSpread = 26;
    static bool LightPaper(int i)
    {
        int b = px[i * 4], g = px[i * 4 + 1], r = px[i * 4 + 2];
        int mn = Math.Min(r, Math.Min(g, b)), mx = Math.Max(r, Math.Max(g, b));
        return mn >= LightMin && mx - mn <= LightSpread;
    }

    static bool ShadowLike(int i)
    {
        double b = px[i * 4], g = px[i * 4 + 1], r = px[i * 4 + 2];
        double kr = r / bgR, kg = g / bgG, kb = b / bgB;
        double mx = Math.Max(kr, Math.Max(kg, kb)), mn = Math.Min(kr, Math.Min(kg, kb));
        return mn >= ShadowMinK && mx <= 1.08 && (mx - mn) <= ShadowSpread;
    }

    public static int SampleTol = 30;

    // excl: flattened [x0,y0,x1,y1,...] fractions forced to background; samples: [x,y,...] fractions of extra backdrop colours
    public static string Process(string input, string output, double[] excl, double[] samples)
    {
        Bitmap src;
        using (var tmp = new Bitmap(input)) src = new Bitmap(tmp.Width, tmp.Height, PixelFormat.Format32bppArgb);
        using (var tmp = new Bitmap(input)) using (var g = Graphics.FromImage(src)) g.DrawImage(tmp, 0, 0, tmp.Width, tmp.Height);
        W = src.Width; H = src.Height;
        var rect = new Rectangle(0, 0, W, H);
        var data = src.LockBits(rect, ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
        px = new byte[W * H * 4];
        Marshal.Copy(data.Scan0, px, 0, px.Length);

        // 1. Background colour: median of a border ring
        var rs = new List<int>(); var gs = new List<int>(); var bs = new List<int>();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
        {
            if (x > 6 && y > 6 && x < W - 7 && y < H - 7) continue;
            int i = y * W + x; bs.Add(px[i * 4]); gs.Add(px[i * 4 + 1]); rs.Add(px[i * 4 + 2]);
        }
        rs.Sort(); gs.Sort(); bs.Sort();
        bgR = rs[rs.Count / 2]; bgG = gs[gs.Count / 2]; bgB = bs[bs.Count / 2];

        // 2. Candidate background = parchment-like or darker-parchment (shadow) pixels; outside ROI is forced background
        int n = W * H;
        var extra = new List<int[]>();
        for (int s = 0; s + 1 < samples.Length; s += 2)
        {
            int sx = (int)(samples[s] * W), sy = (int)(samples[s + 1] * H); long sr = 0, sg = 0, sb = 0, c = 0;
            for (int y = Math.Max(0, sy - 4); y <= Math.Min(H - 1, sy + 4); y++) for (int x = Math.Max(0, sx - 4); x <= Math.Min(W - 1, sx + 4); x++)
            { int i = y * W + x; sb += px[i * 4]; sg += px[i * 4 + 1]; sr += px[i * 4 + 2]; c++; }
            extra.Add(new[] { (int)(sr / c), (int)(sg / c), (int)(sb / c) });
        }
        var cand = new bool[n];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
        {
            int i = y * W + x;
            bool excluded = false;
            for (int e = 0; e + 3 < excl.Length; e += 4)
                if (x >= excl[e] * W && y >= excl[e + 1] * H && x < excl[e + 2] * W && y < excl[e + 3] * H) { excluded = true; break; }
            bool sampled = false;
            foreach (var c in extra)
            {
                int dr = px[i * 4 + 2] - c[0], dg = px[i * 4 + 1] - c[1], db = px[i * 4] - c[2];
                if (dr * dr + dg * dg + db * db < SampleTol * SampleTol) { sampled = true; break; }
            }
            cand[i] = excluded || sampled || Dist(i) < BgTol || ShadowLike(i) || LightPaper(i);
        }

        // 3. Flood fill from the border through candidates
        var bg = new bool[n];
        var q = new Queue<int>();
        for (int x = 0; x < W; x++) { Seed(x, 0, cand, bg, q); Seed(x, H - 1, cand, bg, q); }
        for (int y = 0; y < H; y++) { Seed(0, y, cand, bg, q); Seed(W - 1, y, cand, bg, q); }
        while (q.Count > 0)
        {
            int i = q.Dequeue(); int x = i % W, y = i / W;
            if (x > 0) Visit(i - 1, cand, bg, q);
            if (x < W - 1) Visit(i + 1, cand, bg, q);
            if (y > 0) Visit(i - W, cand, bg, q);
            if (y < H - 1) Visit(i + W, cand, bg, q);
        }

        // 4. Foreground components (8-connected); keep the largest
        var label = new int[n]; int next = 0; var areas = new List<int> { 0 };
        var boxes = new List<int[]> { null };
        for (int s = 0; s < n; s++)
        {
            if (bg[s] || label[s] != 0) continue;
            next++; int area = 0; int bx0 = W, by0 = H, bx1 = 0, by1 = 0;
            label[s] = next; q.Enqueue(s);
            while (q.Count > 0)
            {
                int i = q.Dequeue(); area++; int x = i % W, y = i / W;
                if (x < bx0) bx0 = x; if (x > bx1) bx1 = x; if (y < by0) by0 = y; if (y > by1) by1 = y;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                {
                    int nx = x + dx, ny = y + dy; if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
                    int j = ny * W + nx; if (bg[j] || label[j] != 0) continue;
                    label[j] = next; q.Enqueue(j);
                }
            }
            areas.Add(area); boxes.Add(new[] { bx0, by0, bx1, by1 });
        }
        int main = 1; for (int k = 2; k <= next; k++) if (areas[k] > areas[main]) main = k;
        var mb = boxes[main];
        var keep = new bool[next + 1]; keep[main] = true;
        // Pixels within NearR of the main figure (BFS ring); small fragments touching it are kept (sparkles, loose strands)
        var dist = new int[n];
        for (int i = 0; i < n; i++) { dist[i] = label[i] == main ? 0 : int.MaxValue; if (dist[i] == 0) q.Enqueue(i); }
        while (q.Count > 0)
        {
            int i = q.Dequeue(); if (dist[i] >= NearR) continue; int x = i % W, y = i / W;
            int[] nb = { x > 0 ? i - 1 : -1, x < W - 1 ? i + 1 : -1, y > 0 ? i - W : -1, y < H - 1 ? i + W : -1 };
            foreach (var j in nb) { if (j < 0 || dist[j] != int.MaxValue) continue; dist[j] = dist[i] + 1; q.Enqueue(j); }
        }
        for (int i = 0; i < n; i++)
        {
            int k = label[i]; if (k == 0 || keep[k] || dist[i] == int.MaxValue) continue;
            if (areas[k] < areas[main] * FragMaxRatio) keep[k] = true;
        }
        var fg = new bool[n];
        for (int i = 0; i < n; i++) fg[i] = label[i] != 0 && keep[label[i]];

        // 5. Remove large enclosed parchment pockets (between legs, arm and body)
        var seen = new bool[n]; var pocket = new List<int>();
        for (int s = 0; s < n; s++)
        {
            if (!fg[s] || seen[s] || Dist(s) >= HoleTol) continue;
            pocket.Clear(); seen[s] = true; q.Enqueue(s);
            while (q.Count > 0)
            {
                int i = q.Dequeue(); pocket.Add(i); int x = i % W, y = i / W;
                int[] nb = { x > 0 ? i - 1 : -1, x < W - 1 ? i + 1 : -1, y > 0 ? i - W : -1, y < H - 1 ? i + W : -1 };
                foreach (var j in nb) { if (j < 0 || seen[j] || !fg[j] || Dist(j) >= HoleTol) continue; seen[j] = true; q.Enqueue(j); }
            }
            if (pocket.Count >= HoleMinArea) foreach (var i in pocket) fg[i] = false;
        }

        // 6. Alpha: opaque inside, soft one-pixel edge against background
        int cx0 = W, cy0 = H, cx1 = 0, cy1 = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
        {
            int i = y * W + x;
            if (!fg[i]) { px[i * 4 + 3] = 0; continue; }
            bool edge = (x > 0 && !fg[i - 1]) || (x < W - 1 && !fg[i + 1]) || (y > 0 && !fg[i - W]) || (y < H - 1 && !fg[i + W]);
            int a = 255;
            if (edge) a = Math.Min(255, Math.Max(60, Dist(i) * 255 / (BgTol * 2)));
            px[i * 4 + 3] = (byte)a;
            if (x < cx0) cx0 = x; if (x > cx1) cx1 = x; if (y < cy0) cy0 = y; if (y > cy1) cy1 = y;
        }
        Marshal.Copy(px, 0, data.Scan0, px.Length);
        src.UnlockBits(data);

        // 7. Crop with padding
        cx0 = Math.Max(0, cx0 - Pad); cy0 = Math.Max(0, cy0 - Pad);
        cx1 = Math.Min(W - 1, cx1 + Pad); cy1 = Math.Min(H - 1, cy1 + Pad);
        var crop = new Rectangle(cx0, cy0, cx1 - cx0 + 1, cy1 - cy0 + 1);
        using (var outBmp = src.Clone(crop, PixelFormat.Format32bppArgb)) outBmp.Save(output, ImageFormat.Png);
        src.Dispose();
        return string.Format("bg=({0},{1},{2}) comps={3} main={4} crop={5}x{6}@{7},{8}", bgR, bgG, bgB, next, areas[main], crop.Width, crop.Height, cx0, cy0);
    }

    static void Seed(int x, int y, bool[] cand, bool[] bg, Queue<int> q) { int i = y * W + x; if (cand[i] && !bg[i]) { bg[i] = true; q.Enqueue(i); } }
    static void Visit(int j, bool[] cand, bool[] bg, Queue<int> q) { if (cand[j] && !bg[j]) { bg[j] = true; q.Enqueue(j); } }

    // Contact sheet over the app background colour, to review results
    public static void Sheet(string[] files, string output, int cellW, int cellH, int cols)
    {
        int rows = (files.Length + cols - 1) / cols;
        using (var sheet = new Bitmap(cols * cellW, rows * cellH))
        using (var g = Graphics.FromImage(sheet))
        {
            g.Clear(Color.FromArgb(0xEA, 0xF1, 0xFC));
            g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
            for (int k = 0; k < files.Length; k++)
            {
                using (var im = new Bitmap(files[k]))
                {
                    double s = Math.Min((double)(cellW - 6) / im.Width, (double)(cellH - 6) / im.Height);
                    int w = (int)(im.Width * s), h = (int)(im.Height * s);
                    int x = (k % cols) * cellW + (cellW - w) / 2, y = (k / cols) * cellH + (cellH - h) / 2;
                    g.DrawImage(im, x, y, w, h);
                }
            }
            sheet.Save(output, ImageFormat.Png);
        }
    }
}
