import gzip, struct, collections, sys


class R:
    def __init__(self, b):
        self.b = b
        self.i = 0

    def u1(self):
        v = self.b[self.i]; self.i += 1; return v

    def i1(self):
        v = struct.unpack_from('>b', self.b, self.i)[0]; self.i += 1; return v

    def i2(self):
        v = struct.unpack_from('>h', self.b, self.i)[0]; self.i += 2; return v

    def i4(self):
        v = struct.unpack_from('>i', self.b, self.i)[0]; self.i += 4; return v

    def i8(self):
        v = struct.unpack_from('>q', self.b, self.i)[0]; self.i += 8; return v

    def f4(self):
        v = struct.unpack_from('>f', self.b, self.i)[0]; self.i += 4; return v

    def f8(self):
        v = struct.unpack_from('>d', self.b, self.i)[0]; self.i += 8; return v

    def s(self):
        n = struct.unpack_from('>H', self.b, self.i)[0]; self.i += 2
        v = self.b[self.i:self.i + n].decode('utf-8', 'replace'); self.i += n; return v

    def payload(self, t):
        if t == 1: return self.i1()
        if t == 2: return self.i2()
        if t == 3: return self.i4()
        if t == 4: return self.i8()
        if t == 5: return self.f4()
        if t == 6: return self.f8()
        if t == 7:
            n = self.i4(); v = self.b[self.i:self.i + n]; self.i += n; return v
        if t == 8: return self.s()
        if t == 9:
            it = self.u1(); n = self.i4(); return [self.payload(it) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                ct = self.u1()
                if ct == 0: break
                name = self.s()
                out[name] = self.payload(ct)
            return out
        if t == 11:
            n = self.i4(); v = list(struct.unpack_from('>%di' % n, self.b, self.i)); self.i += 4 * n; return v
        if t == 12:
            n = self.i4(); v = list(struct.unpack_from('>%dq' % n, self.b, self.i)); self.i += 8 * n; return v
        raise ValueError('tag %d' % t)


def read(path):
    raw = gzip.open(path, 'rb').read()
    r = R(raw)
    assert r.u1() == 10
    r.s()
    return r.payload(10)


root = read(sys.argv[1])
palette = [p['Name'] for p in root['palette']]
counts = collections.Counter()
for b in root['blocks']:
    counts[palette[b['state']]] += 1
total = sum(counts.values())
print('size', root['size'], ' cells', total, ' distinct', len(counts))
for name, n in counts.most_common(60):
    print('%8d  %6.3f%%  %s' % (n, 100.0 * n / total, name))
