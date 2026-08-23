/** Domain entity representing a user's goal profile. */
export interface Goal {
  id: string;
  userId: string;
  name: string;
  category: string;
  keywords: string[];
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}
