/**
 * Zone of the body drawing (`shared/component/body-map`, parts of react-native-body-highlighter)
 * where each muscle shows and is felt, by its frozen code (`Muscle.code`), decision D123. Independent
 * from the catalog region, which feeds the region bars: the quadratus lumborum is in the region
 * Abdomen / Pelvis but drawn on the lower back. A muscle without a zone (deep or invisible ones,
 * a muscle added later) is not drawn but still counts in its region.
 */
export const ZONE_OF_MUSCLE: Readonly<Record<string, string>> = {
  'pectoralis-major': 'chest', 'pectoralis-major-pars-clavicularis': 'chest',
  'pectoralis-major-pars-sternocostalis': 'chest', 'pectoralis-minor': 'chest',
  'musculi-abdominis': 'abs', 'rectus-abdominis': 'abs', 'transversus-abdominis': 'abs',
  'obliquus-abdominis': 'obliques',
  'deltoideus': 'deltoids', 'deltoideus-pars-clavicularis': 'deltoids', 'deltoideus-pars-acromialis': 'deltoids',
  'deltoideus-pars-spinalis': 'deltoids', 'rotator-cuff': 'deltoids', 'supraspinatus': 'deltoids',
  'infraspinatus': 'deltoids', 'teres-minor': 'deltoids', 'subscapularis': 'deltoids',
  'biceps-brachii': 'biceps', 'brachialis': 'biceps',
  'triceps-brachii': 'triceps',
  'musculi-antebrachii': 'forearm', 'brachioradialis': 'forearm', 'flexores-antebrachii': 'forearm',
  'extensores-antebrachii': 'forearm',
  'trapezius': 'trapezius', 'trapezius-pars-descendens': 'trapezius', 'trapezius-pars-transversa': 'trapezius',
  'trapezius-pars-ascendens': 'trapezius', 'levator-scapulae': 'trapezius',
  'latissimus-dorsi': 'upper-back', 'musculi-rhomboidei': 'upper-back', 'teres-major': 'upper-back',
  'erector-spinae': 'lower-back', 'transversospinales': 'lower-back', 'quadratus-lumborum': 'lower-back',
  'musculi-glutei': 'gluteal', 'gluteus-maximus': 'gluteal', 'gluteus-medius': 'gluteal', 'gluteus-minimus': 'gluteal',
  'rotatores-laterales-femoris': 'gluteal', 'piriformis': 'gluteal', 'obturator-internus': 'gluteal',
  'obturator-externus': 'gluteal', 'gemellus-superior': 'gluteal', 'gemellus-inferior': 'gluteal',
  'quadratus-femoris': 'gluteal', 'tensor-fasciae-latae': 'gluteal',
  'quadriceps-femoris': 'quadriceps', 'rectus-femoris': 'quadriceps', 'vastus-lateralis': 'quadriceps',
  'vastus-medialis': 'quadriceps', 'vastus-intermedius': 'quadriceps', 'sartorius': 'quadriceps',
  'musculi-adductores': 'adductors', 'adductor-magnus': 'adductors', 'adductor-longus': 'adductors',
  'adductor-brevis': 'adductors', 'pectineus': 'adductors', 'gracilis': 'adductors',
  'musculi-ischiocrurales': 'hamstring', 'biceps-femoris': 'hamstring', 'semitendinosus': 'hamstring',
  'semimembranosus': 'hamstring',
  'triceps-surae': 'calves', 'gastrocnemius': 'calves', 'soleus': 'calves', 'tibialis-posterior': 'calves',
  'musculi-fibulares': 'calves', 'fibularis-longus': 'calves', 'fibularis-brevis': 'calves',
  'tibialis-anterior': 'tibialis'
};

/** The zone of a muscle, or null when it is not drawn. */
export function zoneOf(code: string | null | undefined): string | null {
  return code ? ZONE_OF_MUSCLE[code] ?? null : null;
}
